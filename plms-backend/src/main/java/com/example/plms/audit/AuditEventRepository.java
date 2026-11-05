package com.example.plms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Deliberately exposes only insert and read operations (append-only by design). */
public interface AuditEventRepository extends Repository<AuditEvent, String> {

    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findAll(Pageable pageable);

    Page<AuditEvent> findByEntityId(String entityId, Pageable pageable);

    List<AuditEvent> findByEntityIdOrderByOccurredAtAsc(String entityId);

    List<AuditEvent> findByAction(String action);

    long count();
}
