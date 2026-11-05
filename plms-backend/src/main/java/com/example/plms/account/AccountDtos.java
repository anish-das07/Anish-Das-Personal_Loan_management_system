package com.example.plms.account;

import com.example.plms.core.money.EmiCalculator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(String loanAccountId, String reference, String applicationId, String productName,
                                  BigDecimal principal, BigDecimal annualInterestRate, int tenureMonths,
                                  BigDecimal processingFeePercent, BigDecimal monthlyEmi,
                                  BigDecimal outstandingPrincipal, String status, Instant createdAt,
                                  Instant disbursedAt, Instant closedAt) {
        public static AccountResponse from(LoanAccount a) {
            return new AccountResponse(a.getId(), "LOAN-" + a.getId().substring(0, 8).toUpperCase(), a.getApplicationId(),
                    a.getProductName(), a.getPrincipal(), a.getAnnualRate(), a.getTenureMonths(),
                    a.getProcessingFeePercent(), a.getMonthlyEmi(), a.getOutstandingPrincipal(), a.getStatus(),
                    a.getCreatedAt(), a.getDisbursedAt(), a.getClosedAt());
        }
    }

    public record InstallmentView(String installmentId, int installmentNo, LocalDate dueDate, BigDecimal openingBalance,
                                  BigDecimal principal, BigDecimal interest, BigDecimal fee, BigDecimal total,
                                  BigDecimal closingBalance, String status, Instant paidAt) {
        public static InstallmentView from(EmiInstallment i) {
            return new InstallmentView(i.getId(), i.getInstallmentNo(), i.getDueDate(), i.getOpeningBalance(),
                    i.getPrincipalDue(), i.getInterestDue(), i.getFeeDue(), i.getTotalDue(), i.getPrincipalBalance(),
                    i.getStatus(), i.getPaidAt());
        }
    }

    public record ScheduleResponse(String loanAccountId, BigDecimal annualInterestRate, int tenureMonths,
                                   BigDecimal monthlyEmi, BigDecimal totalInterest, BigDecimal totalPayment,
                                   String scheduleStatus, String roundingPolicy, List<InstallmentView> installments) {
    }

    public record OverdueRow(String loanAccountId, int installmentNo, LocalDate dueDate, long daysOverdue,
                             BigDecimal outstanding, String status) {
        public static OverdueRow from(EmiInstallment i, LocalDate asOf) {
            return new OverdueRow(i.getLoanAccountId(), i.getInstallmentNo(), i.getDueDate(),
                    ChronoUnit.DAYS.between(i.getDueDate(), asOf), i.getTotalDue(), i.getStatus());
        }
    }

    public record QuoteRequest(
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 13, fraction = 2) BigDecimal principal,
            @NotNull @DecimalMin("0.00") @DecimalMax("99.99") BigDecimal annualRate,
            @NotNull @Min(1) @Max(600) Integer months,
            String productId) {
    }

    public record QuoteResponse(BigDecimal principal, BigDecimal annualRate, int months, BigDecimal monthlyEmi,
                                BigDecimal exactEmi, BigDecimal totalInterest, BigDecimal totalPayment,
                                String roundingPolicy, String disclaimer, List<QuoteLine> installments) {
        public static QuoteResponse from(BigDecimal principal, BigDecimal rate, int months, EmiCalculator.Quote q) {
            return new QuoteResponse(principal, rate, months, q.monthlyEmi(), q.exactEmi().setScale(6,
                    java.math.RoundingMode.HALF_UP), q.totalInterest(), q.totalPayment(), ROUNDING_POLICY,
                    "Estimate only; final approved terms may differ.",
                    q.installments().stream().map(QuoteLine::from).toList());
        }
    }

    public record QuoteLine(int installmentNo, LocalDate dueDate, BigDecimal openingBalance, BigDecimal principal,
                            BigDecimal interest, BigDecimal total, BigDecimal closingBalance) {
        static QuoteLine from(EmiCalculator.ScheduleLine l) {
            return new QuoteLine(l.installmentNo(), l.dueDate(), l.openingBalance(), l.principal(), l.interest(),
                    l.total(), l.closingBalance());
        }
    }

    public static final String ROUNDING_POLICY = "HALF_UP to 2 decimals per installment; final installment "
            + "absorbs the rounding residue so the closing balance is 0.00";
}
