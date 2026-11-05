package com.example.plms;

import com.example.plms.approval.LoanDecisionRepository;
import com.example.plms.audit.AuditEventRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Approval: maker-checker, concurrent decisions, evidence completeness, immutable history. */
class ApprovalTest extends IntegrationTestSupport {

    @Autowired
    LoanDecisionRepository decisions;
    @Autowired
    AuditEventRepository audit;

    private String readyForDecision() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        uploadAndVerifyMandatoryDocuments(c, app);
        return app;
    }

    @Test
    void approverApprovesAfterRecommendationAndALoanAccountIsOpened() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        MvcResult r = decide(staff("approver"), app, "APPROVE", "300000.00", 36);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(r, "$.applicationStatus")).isEqualTo("APPROVED");
        assertThat(read(r, "$.loanAccountId")).isNotBlank();
        assertThat(read(r, "$.policyVersion")).isEqualTo("PERSONAL-2026.1");
    }

    @Test
    void makerCannotApproveTheirOwnRecommendation() throws Exception {
        // A single user who holds both maker and checker roles.
        String email = uniqueEmail("dual-role");
        perform(authed(json(post("/api/v1/admin/users"), """
                {"fullName":"Dual Role","email":"%s","password":"Dual-Role-2026","roles":["LOAN_OFFICER","LOAN_APPROVER"]}"""
                .formatted(email)), staff("admin"))).andExpect(status().isCreated());
        Session dual = login(email, "Dual-Role-2026");

        String app = readyForDecision();
        recommend(dual, app);
        MvcResult selfApproval = decide(dual, app, "APPROVE", null, null);
        assertThat(selfApproval.getResponse().getStatus()).isEqualTo(403);
        assertThat(read(selfApproval, "$.code")).isEqualTo("MAKER_CHECKER_VIOLATION");
        assertThat(audit.findByEntityIdOrderByOccurredAtAsc(app))
                .anyMatch(e -> e.getAction().equals("MAKER_CHECKER_VIOLATION"));

        // A different approver can decide.
        assertThat(decide(staff("approver"), app, "APPROVE", null, null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentDecisionsResultInExactlyOneTerminalDecision() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        Session a1 = staff("approver");
        Session a2 = staff("approver2");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (Session approver : List.of(a1, a2)) {
            MockMvc own = newMockMvc();
            results.add(pool.submit(() -> {
                start.await();
                return own.perform(authed(json(post("/api/v1/applications/" + app + "/decision"),
                        approver == a1
                                ? "{\"decision\":\"APPROVE\",\"reasonCode\":\"POLICY_MET\"}"
                                : "{\"decision\":\"REJECT\",\"reasonCode\":\"POLICY_NOT_MET\"}"), approver))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(decisions.findByApplicationIdOrderByDecidedAtAsc(app)).hasSize(1);
    }

    @Test
    void staleExpectedVersionIsRejected() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        perform(authed(json(post("/api/v1/applications/" + app + "/decision"),
                "{\"decision\":\"APPROVE\",\"reasonCode\":\"POLICY_MET\",\"expectedVersion\":0}"), staff("approver")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_STATE"));
    }

    @Test
    void approvalRequiresRecommendationAndVerifiedDocuments() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        assertThat(read(decide(staff("approver"), app, "APPROVE", null, null), "$.code"))
                .isEqualTo("RECOMMENDATION_REQUIRED");
        recommend(staff("officer"), app);
        MvcResult missingDocs = decide(staff("approver"), app, "APPROVE", null, null);
        assertThat(missingDocs.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(missingDocs, "$.code")).isEqualTo("DOCUMENTS_INCOMPLETE");
        assertThat(body(missingDocs)).contains("IDENTITY_PROOF").contains("INCOME_PROOF");
    }

    @Test
    void approvedAmountCannotExceedRequestedAmountAndReasonIsRequired() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        assertThat(read(decide(staff("approver"), app, "APPROVE", "400000.00", 36), "$.code"))
                .isEqualTo("OUTSIDE_PRODUCT_LIMITS");
        perform(authed(json(post("/api/v1/applications/" + app + "/decision"), "{\"decision\":\"APPROVE\"}"),
                staff("approver"))).andExpect(status().isBadRequest());
    }

    @Test
    void returnForCorrectionAllowsResubmissionAsANewRevision() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        assertThat(decide(staff("approver"), app, "RETURN_FOR_CORRECTION", null, null).getResponse().getStatus())
                .isEqualTo(200);
        perform(authed(get("/api/v1/applications/" + app), c))
                .andExpect(jsonPath("$.status").value("RETURNED_FOR_CORRECTION"));
        assertThat(submit(c, app).getResponse().getStatus()).isEqualTo(200);
        perform(authed(get("/api/v1/applications/" + app), c)).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void decidedApplicationCannotBeDecidedAgainAndHistoryIsImmutable() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        assertThat(decide(staff("approver"), app, "REJECT", null, null).getResponse().getStatus()).isEqualTo(200);
        MvcResult again = decide(staff("approver2"), app, "APPROVE", null, null);
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(again, "$.code")).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(decisions.findByApplicationIdOrderByDecidedAtAsc(app)).hasSize(1)
                .first().satisfies(d -> assertThat(d.getDecision()).isEqualTo("REJECT"));
    }

    @Test
    void reviewWorkspaceAggregatesEvidenceAndRestrictsRawScore() throws Exception {
        String app = readyForDecision();
        recommend(staff("officer"), app);
        perform(authed(get("/api/v1/staff/applications/" + app + "/review"), staff("approver")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibility.policyVersion").value("PERSONAL-2026.1"))
                .andExpect(jsonPath("$.missingMandatoryDocuments.length()").value(0))
                .andExpect(jsonPath("$.recommendations[0].recommendation").value("APPROVE"))
                .andExpect(jsonPath("$.credit.score").value(org.hamcrest.Matchers.not("restricted")));
        perform(authed(get("/api/v1/staff/applications/" + app + "/review"), staff("officer")))
                .andExpect(jsonPath("$.credit.score").value("restricted"));
    }
}
