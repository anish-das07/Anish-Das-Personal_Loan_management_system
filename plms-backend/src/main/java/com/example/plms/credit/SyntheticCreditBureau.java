package com.example.plms.credit;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simulated credit bureau. No real bureau is ever contacted. Scores are deterministic per customer
 * so demos and tests are repeatable. The failure mode can be switched at runtime (by an admin
 * endpoint or by tests) to demonstrate timeouts, outages and malformed responses.
 */
@Component
public class SyntheticCreditBureau {

    public enum Mode { NORMAL, TIMEOUT, UNAVAILABLE, MALFORMED }

    public record ProviderResponse(String reference, Integer score) {
    }

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.NORMAL);
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

    /** Number of provider calls made (used to verify bounded retries and fail-fast behaviour). */
    public int callCount() {
        return calls.get();
    }

    public Mode mode() {
        return mode.get();
    }

    public void setMode(Mode newMode) {
        mode.set(newMode == null ? Mode.NORMAL : newMode);
    }

    public ProviderResponse fetchScore(String customerId) {
        calls.incrementAndGet();
        return switch (mode.get()) {
            case TIMEOUT -> {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new TransientProviderException("TIMEOUT");
            }
            case UNAVAILABLE -> throw new TransientProviderException("PROVIDER_UNAVAILABLE");
            case MALFORMED -> new ProviderResponse(null, 9999);
            case NORMAL -> new ProviderResponse("SYN-" + UUID.randomUUID().toString().substring(0, 8),
                    deterministicScore(customerId));
        };
    }

    /** 650-850, stable for a given customer. */
    static int deterministicScore(String customerId) {
        return 650 + Math.floorMod(customerId.hashCode(), 201);
    }

    public static class TransientProviderException extends RuntimeException {
        public TransientProviderException(String reason) {
            super(reason);
        }
    }

    public static class MalformedProviderResponseException extends RuntimeException {
        public MalformedProviderResponseException() {
            super("MALFORMED_RESPONSE");
        }
    }
}
