package com.example.plms.core;

import com.example.plms.core.money.EmiCalculator;
import com.example.plms.core.money.EmiCalculator.ScheduleLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Section 12 worked example and Section 14 EMI scenarios (zero rate, standard rate, final rounding). */
@ExtendWith(MockitoExtension.class)
class EmiCalculatorTest {

    private final EmiCalculator calculator = new EmiCalculator();

    /** The representative test from the case study, verbatim in intent. */
    @Test
    void calculatesPositiveEmiForReducingBalanceLoan() {
        BigDecimal emi = calculator.calculate(new BigDecimal("300000.00"), new BigDecimal("12.00"), 36);
        assertTrue(emi.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(2, emi.scale());
    }

    @Test
    void matchesTheWorkedExample() {
        BigDecimal principal = new BigDecimal("300000.00");
        assertThat(calculator.exactEmi(principal, new BigDecimal("12"), 36).setScale(6, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("9964.292944");
        assertThat(calculator.calculate(principal, new BigDecimal("12"), 36)).isEqualByComparingTo("9964.29");

        List<ScheduleLine> s = calculator.schedule(principal, new BigDecimal("12"), 36, LocalDate.of(2026, 11, 23));
        ScheduleLine first = s.get(0);
        assertThat(first.interest()).isEqualByComparingTo("3000.00");
        assertThat(first.principal()).isEqualByComparingTo("6964.29");
        assertThat(first.total()).isEqualByComparingTo("9964.29");
        assertThat(first.closingBalance()).isEqualByComparingTo("293035.71");
        assertThat(first.dueDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(s.get(1).interest()).isEqualByComparingTo("2930.36");
        assertThat(s.get(2).interest()).isEqualByComparingTo("2860.02");
        assertThat(s.get(35).dueDate()).isEqualTo(LocalDate.of(2029, 10, 23));
    }

    @Test
    void zeroRateSplitsPrincipalEvenlyAndFinalInstallmentAbsorbsResidue() {
        assertThat(calculator.calculate(new BigDecimal("1200"), BigDecimal.ZERO, 12)).isEqualByComparingTo("100.00");
        List<ScheduleLine> s = calculator.schedule(new BigDecimal("1000.00"), BigDecimal.ZERO, 3, null);
        assertThat(s.get(0).total()).isEqualByComparingTo("333.33");
        assertThat(s.get(2).total()).isEqualByComparingTo("333.34");
        assertThat(s).allSatisfy(l -> assertThat(l.interest()).isEqualByComparingTo("0"));
    }

    @ParameterizedTest
    @CsvSource({"300000.00,12.00,36", "50000.00,13.00,12", "1500000.00,11.50,84", "99999.99,7.25,7",
            "1000.00,0.00,3", "250000.00,24.00,60"})
    void finalBalanceIsZeroAndPrincipalPartsSumToPrincipal(String p, String rate, int months) {
        BigDecimal principal = new BigDecimal(p);
        List<ScheduleLine> s = calculator.schedule(principal, new BigDecimal(rate), months, LocalDate.of(2026, 1, 1));
        assertThat(s).hasSize(months);
        assertThat(s.get(months - 1).closingBalance()).isEqualByComparingTo("0.00");
        BigDecimal principalSum = s.stream().map(ScheduleLine::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principalSum).isEqualByComparingTo(principal);
        BigDecimal emi = calculator.calculate(principal, new BigDecimal(rate), months);
        for (int i = 0; i < months - 1; i++) {
            assertThat(s.get(i).total()).isEqualByComparingTo(emi);
            assertThat(s.get(i).total().scale()).isEqualTo(2);
            assertThat(s.get(i + 1).openingBalance()).isEqualByComparingTo(s.get(i).closingBalance());
        }
        // Rounding residue in the final installment stays within a few paise of the EMI.
        assertThat(s.get(months - 1).total().subtract(emi).abs()).isLessThan(new BigDecimal("1.00"));
    }

    @Test
    void quoteTotalsAreSumsOfTheSchedule() {
        EmiCalculator.Quote q = calculator.quote(new BigDecimal("300000.00"), new BigDecimal("12.00"), 36, null);
        assertThat(q.totalPayment()).isEqualByComparingTo(q.totalInterest().add(new BigDecimal("300000.00")));
        assertThat(q.totalInterest()).isEqualByComparingTo("58714.58");
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ZERO, BigDecimal.TEN, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.TEN, new BigDecimal("-1"), 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.TEN, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
