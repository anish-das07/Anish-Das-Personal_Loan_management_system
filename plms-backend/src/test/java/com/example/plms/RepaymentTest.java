package com.example.plms;

import com.example.plms.repayment.RepaymentAllocationRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Repayment: duplicate key, failed payment, unknown result, allocation, overdue. */
class RepaymentTest extends IntegrationTestSupport {

    @Autowired
    RepaymentAllocationRepository allocations;

    private static String key() {
        return "repay-" + UUID.randomUUID();
    }

    @Test
    void successfulPaymentAllocatesInterestThenPrincipalAndIssuesReceipt() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        MvcResult r = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key());
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(r, "$.status")).isEqualTo("SUCCESS");
        assertThat(read(r, "$.reference")).startsWith("SIM-PAY-");
        String id = read(r, "$.repaymentId");

        perform(authed(get("/api/v1/repayments/" + id + "/receipt"), loan.customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestAllocated").value(3000.0))
                .andExpect(jsonPath("$.principalAllocated").value(6964.29))
                .andExpect(jsonPath("$.feeAllocated").value(0.0))
                .andExpect(jsonPath("$.note").value("Simulated payment - no real money movement"));
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId()), loan.customer()))
                .andExpect(jsonPath("$.outstandingPrincipal").value(293035.71));
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/schedule"), loan.customer()))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"));
    }

    @Test
    void duplicateKeyReturnsOriginalResultAndAllocatesOnce() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        String key = key();
        MvcResult first = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key);
        MvcResult second = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(second, "$.repaymentId")).isEqualTo(read(first, "$.repaymentId"));
        assertThat(read(second, "$.reference")).isEqualTo(read(first, "$.reference"));
        String installmentId = allocations.findByRepaymentId(read(first, "$.repaymentId")).orElseThrow().getInstallmentId();
        assertThat(allocations.findByInstallmentId(installmentId)).hasSize(1);

        MvcResult reused = pay(loan.customer(), loan.loanAccountId(), 2, "9964.29", "SIMULATED", key);
        assertThat(read(reused, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void failedPaymentLeavesDuesOutstandingAndHasNoReceipt() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        MvcResult r = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED_DECLINE", key());
        assertThat(read(r, "$.status")).isEqualTo("FAILED");
        assertThat(read(r, "$.receiptUrl")).isNull();
        perform(authed(get("/api/v1/repayments/" + read(r, "$.repaymentId") + "/receipt"), loan.customer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECEIPT_UNAVAILABLE"));
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId()), loan.customer()))
                .andExpect(jsonPath("$.outstandingPrincipal").value(300000.0));
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/schedule"), loan.customer()))
                .andExpect(jsonPath("$.installments[0].status").value("DUE"));
        // The customer can try again with a new key.
        assertThat(read(pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key()), "$.status"))
                .isEqualTo("SUCCESS");
    }

    @Test
    void unknownOutcomeIsReconciledNotChargedTwice() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        MvcResult r = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED_TIMEOUT", key());
        assertThat(read(r, "$.status")).isEqualTo("PENDING");
        String id = read(r, "$.repaymentId");

        MvcResult again = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key());
        assertThat(read(again, "$.code")).isEqualTo("PAYMENT_IN_PROGRESS");

        perform(authed(post("/api/v1/repayments/" + id + "/reconcile"), loan.customer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        perform(authed(post("/api/v1/repayments/" + id + "/reconcile"), loan.customer()))
                .andExpect(status().isConflict());
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/repayments"), loan.customer()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void policyRulesAmountMustMatchAndInstallmentsArePaidInOrder() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        assertThat(read(pay(loan.customer(), loan.loanAccountId(), 1, "5000.00", "SIMULATED", key()), "$.code"))
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(read(pay(loan.customer(), loan.loanAccountId(), 2, "9964.29", "SIMULATED", key()), "$.code"))
                .isEqualTo("OUT_OF_ORDER");
        perform(authed(json(post("/api/v1/repayments").header("Idempotency-Key", key()), """
                {"loanAccountId":"%s","installmentNo":1,"amount":9964.29,"paymentMethod":"REAL_CARD"}"""
                .formatted(loan.loanAccountId())), loan.customer()))
                .andExpect(status().isBadRequest());
        pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key());
        assertThat(read(pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key()), "$.code"))
                .isEqualTo("INSTALLMENT_ALREADY_PAID");
    }

    @Test
    void cannotPayALoanThatIsNotActive() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        MvcResult r = pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key());
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void overdueReportListsUnpaidPastDueInstallmentsForFinanceOnly() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        pay(loan.customer(), loan.loanAccountId(), 1, "9964.29", "SIMULATED", key());
        // As of a date after installments 1-3 fall due: #1 is paid, #2 and #3 are overdue.
        String firstDue = read(perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/schedule"),
                loan.customer())).andReturn(), "$.installments[0].dueDate");
        String asOf = java.time.LocalDate.parse(firstDue).plusMonths(2).plusDays(5).toString();
        MvcResult r = perform(authed(get("/api/v1/reports/overdue?asOf=" + asOf + "&size=100"), staff("finance")))
                .andExpect(status().isOk()).andReturn();
        String json = body(r);
        assertThat(json).contains(loan.loanAccountId());
        assertThat(com.jayway.jsonpath.JsonPath.<java.util.List<Integer>>read(json,
                "$.content[?(@.loanAccountId=='" + loan.loanAccountId() + "')].installmentNo"))
                .containsExactlyInAnyOrder(2, 3);
        assertThat(com.jayway.jsonpath.JsonPath.<java.util.List<Integer>>read(json,
                "$.content[?(@.loanAccountId=='" + loan.loanAccountId() + "' && @.installmentNo==2)].daysOverdue"))
                .containsExactly((int) java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(firstDue).plusMonths(1), java.time.LocalDate.parse(asOf)));
        perform(authed(get("/api/v1/reports/overdue"), loan.customer())).andExpect(status().isForbidden());
    }
}
