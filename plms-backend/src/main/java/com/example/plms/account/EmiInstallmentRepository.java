package com.example.plms.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmiInstallmentRepository extends JpaRepository<EmiInstallment, String> {

    List<EmiInstallment> findByLoanAccountIdOrderByInstallmentNoAsc(String loanAccountId);

    Optional<EmiInstallment> findByLoanAccountIdAndInstallmentNo(String loanAccountId, int installmentNo);

    Optional<EmiInstallment> findFirstByLoanAccountIdAndStatusNotOrderByInstallmentNoAsc(String loanAccountId,
                                                                                        String status);

    long countByLoanAccountIdAndStatusNot(String loanAccountId, String status);

    @Query("select i from EmiInstallment i where i.dueDate < :asOf and i.status <> 'PAID'")
    Page<EmiInstallment> findOverdue(@Param("asOf") LocalDate asOf, Pageable pageable);

    @Query("select i from EmiInstallment i where i.dueDate < :asOf and i.status = 'DUE'")
    List<EmiInstallment> findDueBefore(@Param("asOf") LocalDate asOf);
}
