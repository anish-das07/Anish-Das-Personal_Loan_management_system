package com.example.plms.audit;

import com.example.plms.common.CorrelationIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class AuditService {

    /** Keys whose values are never written to audit metadata. */
    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|token|secret|authorization|otp|pan|aadhaar|ssn|content).*");

    private final AuditEventRepository repository;
    private final Clock clock;
    /** Last issued timestamp; keeps the audit trail strictly ordered even within one microsecond. */
    private final AtomicReference<Instant> lastTimestamp = new AtomicReference<>(Instant.EPOCH);

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records an event inside the caller's transaction, so it commits or rolls back with the change. */
    @Transactional
    public void record(String actorId, String action, String entityType, String entityId, String outcome,
                       Map<String, ?> metadata) {
        repository.save(build(actorId, action, entityType, entityId, outcome, metadata));
    }

    /** Records an event that must survive a rollback of the caller (security events, failed attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(String actorId, String action, String entityType, String entityId,
                                    String outcome, Map<String, ?> metadata) {
        repository.save(build(actorId, action, entityType, entityId, outcome, metadata));
    }

    private AuditEvent build(String actorId, String action, String entityType, String entityId, String outcome,
                             Map<String, ?> metadata) {
        return new AuditEvent(actorId, action, entityType, entityId, CorrelationIdFilter.currentId(), outcome,
                redact(metadata), nextTimestamp());
    }

    /** Microsecond precision (what the database stores), strictly increasing per application instance. */
    private Instant nextTimestamp() {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return lastTimestamp.updateAndGet(last -> now.isAfter(last) ? now : last.plus(1, ChronoUnit.MICROS));
    }

    static String redact(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        new TreeMap<>(metadata).forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            String value = SENSITIVE_KEY.matcher(k).matches() ? "[REDACTED]" : String.valueOf(v);
            sb.append('"').append(k).append("\":\"").append(value.replace("\"", "'")).append('"');
        });
        String json = sb.append('}').toString();
        return json.length() > 2000 ? json.substring(0, 1997) + "..." : json;
    }
}
