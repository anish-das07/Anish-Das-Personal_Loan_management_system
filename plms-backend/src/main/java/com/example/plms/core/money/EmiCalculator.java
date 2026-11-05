package com.example.plms.core.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reducing-balance EMI calculator.
 *
 * <p>Framework-free on purpose, so it can be unit-tested and reused by any service.
 *
 * <p>Documented rounding policy:
 * <ul>
 *   <li>Intermediate maths uses {@link MathContext#DECIMAL128} (34 significant digits).</li>
 *   <li>EMI, interest and principal amounts are rounded to 2 decimals with {@link RoundingMode#HALF_UP}.</li>
 *   <li>Each installment's interest = round(opening balance x monthly rate).</li>
 *   <li>Principal = EMI - interest. The final installment repays the remaining balance exactly,
 *       so the rounding residue is absorbed there and the closing balance is always 0.00.</li>
 * </ul>
 */
public final class EmiCalculator {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final int MAX_MONTHS = 600;
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(HUNDRED, MC).divide(TWELVE, MC);
    }

    /** Unrounded EMI, useful for display of the exact value. */
    public BigDecimal exactEmi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        validate(principal, annualRatePercent, months);
        BigDecimal r = monthlyRate(annualRatePercent);
        if (r.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), MC);
        }
        BigDecimal factor = BigDecimal.ONE.add(r, MC).pow(months, MC);
        return principal.multiply(r, MC).multiply(factor, MC)
                .divide(factor.subtract(BigDecimal.ONE, MC), MC);
    }

    public BigDecimal calculate(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        return exactEmi(principal, annualRatePercent, months).setScale(SCALE, ROUNDING);
    }

    public List<ScheduleLine> schedule(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                       LocalDate firstDueDate) {
        BigDecimal emi = calculate(principal, annualRatePercent, months);
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal balance = principal.setScale(SCALE, ROUNDING);
        List<ScheduleLine> lines = new ArrayList<>(months);
        for (int i = 1; i <= months; i++) {
            BigDecimal opening = balance;
            BigDecimal interest = opening.multiply(r, MC).setScale(SCALE, ROUNDING);
            BigDecimal principalPart;
            if (i == months) {
                principalPart = opening;
            } else {
                principalPart = emi.subtract(interest);
                if (principalPart.compareTo(opening) > 0) {
                    principalPart = opening;
                }
            }
            BigDecimal total = principalPart.add(interest);
            balance = opening.subtract(principalPart);
            LocalDate due = firstDueDate == null ? null : firstDueDate.plusMonths(i - 1L);
            lines.add(new ScheduleLine(i, due, opening, principalPart, interest, total, balance));
        }
        return Collections.unmodifiableList(lines);
    }

    public Quote quote(BigDecimal principal, BigDecimal annualRatePercent, int months, LocalDate firstDueDate) {
        List<ScheduleLine> lines = schedule(principal, annualRatePercent, months, firstDueDate);
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal totalPayment = BigDecimal.ZERO.setScale(SCALE);
        for (ScheduleLine l : lines) {
            totalInterest = totalInterest.add(l.interest());
            totalPayment = totalPayment.add(l.total());
        }
        return new Quote(calculate(principal, annualRatePercent, months),
                exactEmi(principal, annualRatePercent, months), totalInterest, totalPayment, lines);
    }

    private static void validate(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("Principal must be positive.");
        }
        if (annualRatePercent == null || annualRatePercent.signum() < 0) {
            throw new IllegalArgumentException("Annual rate must be zero or positive.");
        }
        if (months <= 0 || months > MAX_MONTHS) {
            throw new IllegalArgumentException("Tenure must be between 1 and " + MAX_MONTHS + " months.");
        }
    }

    public record ScheduleLine(int installmentNo, LocalDate dueDate, BigDecimal openingBalance,
                               BigDecimal principal, BigDecimal interest, BigDecimal total,
                               BigDecimal closingBalance) {
    }

    public record Quote(BigDecimal monthlyEmi, BigDecimal exactEmi, BigDecimal totalInterest,
                        BigDecimal totalPayment, List<ScheduleLine> installments) {
    }
}
