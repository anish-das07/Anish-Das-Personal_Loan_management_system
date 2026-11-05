package com.example.plms.application;

import com.example.plms.core.workflow.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String> {

    List<LoanApplication> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<LoanApplication> findByCustomerIdAndCreateIdempotencyKey(String customerId, String key);

    Page<LoanApplication> findByStatusIn(Collection<ApplicationStatus> statuses, Pageable pageable);

    long countByStatus(ApplicationStatus status);

    /** Serialises concurrent decisions on one application (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanApplication a where a.id = :id")
    Optional<LoanApplication> findForUpdate(@Param("id") String id);
}
