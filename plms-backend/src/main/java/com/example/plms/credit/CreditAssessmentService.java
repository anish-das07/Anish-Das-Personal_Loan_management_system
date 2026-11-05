package com.example.plms.credit;

import com.example.plms.audit.AuditService;
import com.example.plms.common.PlmsProperties;
import com.example.plms.core.credit.ScoreBands;
import com.example.plms.credit.SyntheticCreditBureau.MalformedProviderResponseException;
import com.example.plms.credit.SyntheticCreditBureau.ProviderResponse;
import com.example.plms.credit.SyntheticCreditBureau.TransientProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Calls the synthetic provider with an explicit timeout, a bounded retry (transient failures only;
 * the call is a read so it is safe to repeat) and a circuit breaker. Any failure produces a
 * PENDING_PROVIDER record, so the application moves to pending/manual review instead of being
 * treated as a failed score.
 */
@Service
public class CreditAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(CreditAssessmentService.class);

    private final SyntheticCreditBureau bureau;
    private final CreditAssessmentRepository repository;
    private final AuditService audit;
    private final Clock clock;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CreditAssessmentService(SyntheticCreditBureau bureau, CreditAssessmentRepository repository,
                                   AuditService audit, PlmsProperties properties, Clock clock) {
        this.bureau = bureau;
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
        PlmsProperties.Credit cfg = properties.credit();
        this.timeout = cfg.timeout();
        this.circuitBreaker = CircuitBreaker.of("creditProvider", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cfg.circuitWindowSize())
                .minimumNumberOfCalls(cfg.circuitWindowSize())
                .failureRateThreshold(cfg.circuitFailureRate())
                .waitDurationInOpenState(cfg.circuitWaitOpen())
                .recordExceptions(TransientProviderException.class, MalformedProviderResponseException.class)
                .build());
        this.retry = Retry.of("creditProvider", RetryConfig.custom()
                .maxAttempts(Math.max(1, cfg.retryAttempts()))
                .waitDuration(Duration.ofMillis(50))
                .retryExceptions(TransientProviderException.class)
                .build());
    }

    @Transactional
    public CreditAssessment assess(String applicationId, String customerId, String requestedBy) {
        Instant now = Instant.now(clock);
        CreditAssessment result;
        try {
            ProviderResponse response = guardedCall(customerId).get();
            result = CreditAssessment.available(applicationId, customerId, response.reference(), response.score(),
                    ScoreBands.band(response.score()), requestedBy, now);
        } catch (CallNotPermittedException e) {
            result = CreditAssessment.pending(applicationId, customerId, "CIRCUIT_OPEN", requestedBy, now);
        } catch (MalformedProviderResponseException e) {
            result = CreditAssessment.pending(applicationId, customerId, "MALFORMED_RESPONSE", requestedBy, now);
        } catch (TransientProviderException e) {
            result = CreditAssessment.pending(applicationId, customerId, e.getMessage(), requestedBy, now);
        }
        result = repository.save(result);
        if (!result.isAvailable()) {
            log.warn("Synthetic credit provider unavailable for application {}: {}", applicationId,
                    result.getFailureReason());
        }
        audit.record(requestedBy, "CREDIT_ASSESSMENT_REQUESTED", "CREDIT_ASSESSMENT", applicationId,
                result.isAvailable() ? "SUCCESS" : "PENDING",
                Map.of("status", result.getStatus(), "reason", String.valueOf(result.getFailureReason())));
        return result;
    }

    private Supplier<ProviderResponse> guardedCall(String customerId) {
        Supplier<ProviderResponse> validated = () -> {
            ProviderResponse response = callWithTimeout(customerId);
            if (response == null || response.reference() == null || !ScoreBands.isValid(response.score())) {
                throw new MalformedProviderResponseException();
            }
            return response;
        };
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, validated));
    }

    private ProviderResponse callWithTimeout(String customerId) {
        Future<ProviderResponse> future = executor.submit(() -> bureau.fetchScore(customerId));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TransientProviderException("TIMEOUT");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TransientProviderException t) {
                throw t;
            }
            throw new TransientProviderException("PROVIDER_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientProviderException("INTERRUPTED");
        }
    }

    @Transactional(readOnly = true)
    public List<CreditAssessment> forApplication(String applicationId) {
        return repository.findByApplicationIdOrderByAssessedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public Optional<CreditAssessment> latest(String applicationId) {
        return repository.findFirstByApplicationIdOrderByAssessedAtDesc(applicationId);
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
