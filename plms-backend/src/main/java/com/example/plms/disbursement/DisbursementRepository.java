package com.example.plms.disbursement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DisbursementRepository extends JpaRepository<Disbursement, String> {

    Optional<Disbursement> findByIdempotencyKey(String idempotencyKey);

    List<Disbursement> findByLoanAccountIdOrderByCreatedAtDesc(String loanAccountId);

    boolean existsByLoanAccountIdAndStatusIn(String loanAccountId, Collection<String> statuses);

    long countByStatus(String status);
}
