package com.example.plms.repayment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepaymentRepository extends JpaRepository<Repayment, String> {

    Optional<Repayment> findByIdempotencyKey(String idempotencyKey);

    List<Repayment> findByLoanAccountIdOrderByCreatedAtDesc(String loanAccountId);

    boolean existsByInstallmentIdAndStatus(String installmentId, String status);

    boolean existsByLoanAccountIdAndStatus(String loanAccountId, String status);

    long countByStatus(String status);
}
