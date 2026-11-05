package com.example.plms.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "emi_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uq_emi_installment", columnNames = {"loan_account_id", "installment_no"}),
        indexes = @Index(name = "idx_emi_due_status", columnList = "due_date, status"))
public class EmiInstallment {

    public enum Status { DUE, OVERDUE, PAID }

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "loan_account_id", nullable = false, length = 36)
    private String loanAccountId;
    @Column(name = "installment_no", nullable = false)
    private int installmentNo;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance;
    @Column(name = "principal_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalDue;
    @Column(name = "interest_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestDue;
    @Column(name = "fee_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeDue;
    @Column(name = "total_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDue;
    @Column(name = "principal_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalBalance;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Version
    private Long version;

    protected EmiInstallment() {
    }

    public EmiInstallment(String loanAccountId, int installmentNo, LocalDate dueDate, BigDecimal openingBalance,
                          BigDecimal principalDue, BigDecimal interestDue, BigDecimal totalDue,
                          BigDecimal principalBalance) {
        this.id = UUID.randomUUID().toString();
        this.loanAccountId = loanAccountId;
        this.installmentNo = installmentNo;
        this.dueDate = dueDate;
        this.openingBalance = openingBalance;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.feeDue = BigDecimal.ZERO.setScale(2);
        this.totalDue = totalDue;
        this.principalBalance = principalBalance;
        this.status = Status.DUE.name();
    }

    public void markPaid(Instant now) {
        this.status = Status.PAID.name();
        this.paidAt = now;
    }

    public void markOverdue() {
        if (is(Status.DUE)) {
            this.status = Status.OVERDUE.name();
        }
    }

    public boolean is(Status s) {
        return s.name().equals(status);
    }

    public String getId() {
        return id;
    }

    public String getLoanAccountId() {
        return loanAccountId;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getFeeDue() {
        return feeDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public BigDecimal getPrincipalBalance() {
        return principalBalance;
    }

    public String getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
