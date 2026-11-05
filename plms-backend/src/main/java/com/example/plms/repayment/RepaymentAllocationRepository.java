package com.example.plms.repayment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepaymentAllocationRepository extends JpaRepository<RepaymentAllocation, String> {

    Optional<RepaymentAllocation> findByRepaymentId(String repaymentId);

    List<RepaymentAllocation> findByInstallmentId(String installmentId);
}
