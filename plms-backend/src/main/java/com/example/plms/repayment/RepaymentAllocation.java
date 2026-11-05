package com.example.plms.repayment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/** How a confirmed payment was applied (fees, then interest, then principal). One per repayment. */
@Entity
@Table(name = "repayment_allocation",
        uniqueConstraints = @UniqueConstraint(name = "uq_allocation_repayment", columnNames = "repayment_id"),
        indexes = @Index(name = "idx_allocation_installment", columnList = "installment_id"))
public class RepaymentAllocation {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "repayment_id", nullable = false, length = 36)
    private String repaymentId;
    @Column(name = "installment_id", nullable = false, length = 36)
    private String installmentId;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fee;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interest;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;

    protected RepaymentAllocation() {
    }

    public RepaymentAllocation(String repaymentId, String installmentId, BigDecimal fee, BigDecimal interest,
                               BigDecimal principal) {
        this.id = UUID.randomUUID().toString();
        this.repaymentId = repaymentId;
        this.installmentId = installmentId;
        this.fee = fee;
        this.interest = interest;
        this.principal = principal;
    }

    public String getRepaymentId() {
        return repaymentId;
    }

    public String getInstallmentId() {
        return installmentId;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }
}
