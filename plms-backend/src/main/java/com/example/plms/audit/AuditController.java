package com.example.plms.audit;

import com.example.plms.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    public record AuditEventResponse(String id, String actorId, String action, String entityType, String entityId,
                                     String correlationId, Instant timestamp, String outcome, String metadata) {
        static AuditEventResponse from(AuditEvent e) {
            return new AuditEventResponse(e.getId(), e.getActorId(), e.getAction(), e.getEntityType(),
                    e.getEntityId(), e.getCorrelationId(), e.getOccurredAt(), e.getOutcome(),
                    e.getMetadataRedacted());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(@RequestParam(required = false) String entityId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        Pageable pageable = PageResponse.pageable(page, size, null, Set.of("occurredAt"), "occurredAt");
        var result = entityId == null || entityId.isBlank()
                ? repository.findAll(pageable)
                : repository.findByEntityId(entityId, pageable);
        return PageResponse.of(result, AuditEventResponse::from);
    }
}
