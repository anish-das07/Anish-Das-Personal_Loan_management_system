package com.example.plms;

import com.example.plms.account.EmiInstallmentRepository;
import com.example.plms.disbursement.DisbursementRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Disbursement: idempotency, net amount, failure, unknown outcome and reconciliation. */
class DisbursementTest extends IntegrationTestSupport {

    @Autowired
    DisbursementRepository disbursements;
    @Autowired
    EmiInstallmentRepository installments;

    @Test
    void successfulDisbursementComputesNetAmountActivatesLoanAndGeneratesSchedule() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        MvcResult r = disburse(staff("finance"), loan.loanAccountId(), "disb-loan-1-v1", "300000.00", "3000.00", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(r, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(read(r, "$.netAmount")).isEqualTo("297000.0");
        assertThat(read(r, "$.transactionRef")).startsWith("SIM-DISB-");

        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/schedule"), loan.customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleStatus").value("GENERATED"))
                .andExpect(jsonPath("$.monthlyEmi").value(9964.29))
                .andExpect(jsonPath("$.annualInterestRate").value(12.0))
                .andExpect(jsonPath("$.tenureMonths").value(36))
                .andExpect(jsonPath("$.installments.length()").value(36))
                .andExpect(jsonPath("$.installments[0].interest").value(3000.0))
                .andExpect(jsonPath("$.installments[0].principal").value(6964.29))
                .andExpect(jsonPath("$.installments[35].closingBalance").value(0.0));
        perform(authed(get("/api/v1/applications/" + loan.applicationId()), loan.customer()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalResultWithoutASecondTransfer() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        Session finance = staff("finance");
        MvcResult first = disburse(finance, loan.loanAccountId(), "disb-dup-" + loan.loanAccountId(), "300000.00",
                "3000.00", null);
        MvcResult second = disburse(finance, loan.loanAccountId(), "disb-dup-" + loan.loanAccountId(), "300000.00",
                "3000.00", null);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(second, "$.disbursementId")).isEqualTo(read(first, "$.disbursementId"));
        assertThat(read(second, "$.transactionRef")).isEqualTo(read(first, "$.transactionRef"));
        assertThat(disbursements.findByLoanAccountIdOrderByCreatedAtDesc(loan.loanAccountId())).hasSize(1);
        assertThat(installments.findByLoanAccountIdOrderByInstallmentNoAsc(loan.loanAccountId())).hasSize(36);
    }

    @Test
    void reusingAKeyForADifferentRequestIsAConflict() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        Session finance = staff("finance");
        String key = "disb-reuse-" + loan.loanAccountId();
        disburse(finance, loan.loanAccountId(), key, "300000.00", "3000.00", "FAIL");
        MvcResult r = disburse(finance, loan.loanAccountId(), key, "290000.00", "3000.00", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(r, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void validatesKeyAmountAndAccountState() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        Session finance = staff("finance");
        perform(authed(json(post("/api/v1/loan-accounts/" + loan.loanAccountId() + "/disbursements"),
                "{\"grossAmount\":300000.00,\"feeAmount\":0}"), finance))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        assertThat(read(disburse(finance, loan.loanAccountId(), "too-much-" + loan.loanAccountId(), "300000.01",
                "0.00", null), "$.code")).isEqualTo("AMOUNT_EXCEEDS_APPROVED");
        assertThat(read(disburse(finance, loan.loanAccountId(), "bad-fee-" + loan.loanAccountId(), "300000.00",
                "300000.01", null), "$.code")).isEqualTo("INVALID_FEE");

        disburse(finance, loan.loanAccountId(), "ok-" + loan.loanAccountId(), "300000.00", "3000.00", null);
        MvcResult again = disburse(finance, loan.loanAccountId(), "second-" + loan.loanAccountId(), "300000.00",
                "3000.00", null);
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void failedTransferKeepsLoanAwaitingFundingAndCanBeRetriedWithANewKey() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        Session finance = staff("finance");
        MvcResult failed = disburse(finance, loan.loanAccountId(), "fail-" + loan.loanAccountId(), "300000.00",
                "3000.00", "FAIL");
        assertThat(read(failed, "$.status")).isEqualTo("FAILED");
        assertThat(read(failed, "$.failureReason")).isEqualTo("BENEFICIARY_ACCOUNT_INVALID");
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId()), finance))
                .andExpect(jsonPath("$.status").value("PENDING_DISBURSEMENT"));
        assertThat(installments.findByLoanAccountIdOrderByInstallmentNoAsc(loan.loanAccountId())).isEmpty();

        MvcResult retry = disburse(finance, loan.loanAccountId(), "retry-" + loan.loanAccountId(), "300000.00",
                "3000.00", null);
        assertThat(read(retry, "$.status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void timeoutIsUnknownBlocksRetryAndIsReconciledByReference() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        Session finance = staff("finance");
        MvcResult unknown = disburse(finance, loan.loanAccountId(), "to-" + loan.loanAccountId(), "300000.00",
                "3000.00", "TIMEOUT");
        assertThat(read(unknown, "$.status")).isEqualTo("UNKNOWN");
        String id = read(unknown, "$.disbursementId");

        // Not assumed failed: a new attempt is blocked until the unknown one is reconciled.
        MvcResult blocked = disburse(finance, loan.loanAccountId(), "to2-" + loan.loanAccountId(), "300000.00",
                "3000.00", null);
        assertThat(read(blocked, "$.code")).isEqualTo("DISBURSEMENT_IN_PROGRESS");

        perform(authed(post("/api/v1/disbursements/" + id + "/reconcile"), finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.transactionRef").exists());
        assertThat(installments.findByLoanAccountIdOrderByInstallmentNoAsc(loan.loanAccountId())).hasSize(36);
        perform(authed(post("/api/v1/disbursements/" + id + "/reconcile"), finance))
                .andExpect(status().isConflict());
    }

    @Test
    void financeQueueListsApprovedLoansAwaitingFunding() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        MvcResult r = perform(authed(get("/api/v1/finance/disbursements/eligible"), staff("finance")))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(r)).contains(loan.loanAccountId());
    }
}
