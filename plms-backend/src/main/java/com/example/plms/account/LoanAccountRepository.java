package com.example.plms.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanAccountRepository extends JpaRepository<LoanAccount, String> {

    List<LoanAccount> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<LoanAccount> findByApplicationId(String applicationId);

    List<LoanAccount> findByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanAccount a where a.id = :id")
    Optional<LoanAccount> findForUpdate(@Param("id") String id);
}
