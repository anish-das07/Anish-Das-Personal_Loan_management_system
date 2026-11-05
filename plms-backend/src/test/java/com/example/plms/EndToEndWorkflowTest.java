package com.example.plms;

import com.example.plms.audit.AuditEvent;
import com.example.plms.audit.AuditEventRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Section 14 - End-to-end: a customer goes from registration through simulated closure, with
 * workflow states, notifications and audit consistent across modules (mirrors the Appendix A demo).
 */
class EndToEndWorkflowTest extends IntegrationTestSupport {

    @Autowired
    AuditEventRepository audit;

    @Test
    void registrationToClosure() throws Exception {
        // 1. Register, complete profile, browse a product and get an indicative EMI.
        Session customer = readyCustomer();
        perform(authed(get("/api/v1/loan-products?size=100"), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code=='PERSONAL-FLEX')].availableNow").value(true));
        perform(authed(json(post("/api/v1/emi/quote"), """
                {"principal":60000.00,"annualRate":12.00,"months":6,"productId":"%s"}"""
                .formatted(productId("PERSONAL-FLEX"))), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments.length()").value(6));

        // 2. Draft, submit, track.
        String app = submittedApplication(customer, "60000.00", 6);
        perform(authed(get("/api/v1/applications/" + app), customer))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // 3. Documents: resubmission requested, then verified.
        Session verifier = staff("verifier");
        String id1 = upload(customer, app, "IDENTITY_PROOF", "id.pdf", PDF);
        verifyDocument(verifier, id1);
        String income1 = upload(customer, app, "INCOME_PROOF", "income.pdf", PDF);
        perform(authed(json(post("/api/v1/documents/" + income1 + "/decision"),
                "{\"decision\":\"RESUBMISSION_REQUIRED\",\"reasonCode\":\"IMAGE_UNREADABLE\"}"), verifier))
                .andExpect(status().isOk());
        verifyDocument(verifier, upload(customer, app, "INCOME_PROOF", "income-v2.pdf", PDF));

        // 4. Maker recommends, a different checker approves.
        recommend(staff("officer"), app);
        MvcResult decision = decide(staff("approver"), app, "APPROVE", "60000.00", 6);
        assertThat(decision.getResponse().getStatus()).isEqualTo(200);
        String account = read(decision, "$.loanAccountId");

        // 5. Simulated disbursement.
        Session finance = staff("finance");
        MvcResult disb = disburse(finance, account, "disb-" + UUID.randomUUID(), "60000.00", "600.00", null);
        assertThat(read(disb, "$.netAmount")).isEqualTo("59400.0");

        // 6. Closure is blocked while dues are outstanding.
        perform(authed(post("/api/v1/loan-accounts/" + account + "/closure"), finance))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLOSURE_BLOCKED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("6 installment(s) are unpaid"));

        // 7. One failed payment, then all six installments paid.
        String total1 = installmentTotal(customer, account, 1);
        assertThat(read(pay(customer, account, 1, total1, "SIMULATED_DECLINE", "k-fail"), "$.status"))
                .isEqualTo("FAILED");
        for (int i = 1; i <= 6; i++) {
            MvcResult paid = pay(customer, account, i, installmentTotal(customer, account, i), "SIMULATED",
                    "k-" + account + "-" + i);
            assertThat(read(paid, "$.status")).isEqualTo("SUCCESS");
        }
        perform(authed(get("/api/v1/loan-accounts/" + account), customer))
                .andExpect(jsonPath("$.outstandingPrincipal").value(0.0));

        // 8. Closure succeeds; the application is CLOSED.
        perform(authed(get("/api/v1/loan-accounts/" + account + "/closure-check"), finance))
                .andExpect(jsonPath("$.closable").value(true));
        perform(authed(post("/api/v1/loan-accounts/" + account + "/closure"), finance))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        perform(authed(get("/api/v1/applications/" + app), customer))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        perform(authed(get("/api/v1/applications/" + app + "/history"), customer))
                .andExpect(jsonPath("$[-1].toStatus").value("CLOSED"));

        // 9. Notifications reached the customer; audit trail covers the lifecycle in order.
        MvcResult notes = perform(authed(get("/api/v1/notifications"), customer)).andReturn();
        assertThat(body(notes)).contains("APPLICATION_SUBMITTED", "DOCUMENT_ACTION_REQUIRED", "APPLICATION_DECIDED",
                "LOAN_DISBURSED", "REPAYMENT_RECEIVED", "LOAN_CLOSED");
        assertThat(audit.findByEntityIdOrderByOccurredAtAsc(app)).extracting(AuditEvent::getAction)
                .containsSubsequence("APPLICATION_DRAFT_CREATED", "APPLICATION_SUBMITTED", "ELIGIBILITY_EVALUATED",
                        "APPLICATION_UNDER_REVIEW", "RECOMMENDATION_RECORDED", "APPLICATION_APPROVED",
                        "LOAN_DECISION_APPROVE", "APPLICATION_DISBURSEMENT_PENDING", "APPLICATION_ACTIVE",
                        "APPLICATION_CLOSED");

        // 10. Admin can read the audit trail; another customer still sees nothing.
        perform(authed(get("/api/v1/audit-events?entityId=" + app), staff("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(5)));
        perform(authed(get("/api/v1/loan-accounts/" + account), readyCustomer())).andExpect(status().isNotFound());
    }

    @Test
    void errorResponsesUseTheStandardShapeWithCorrelationId() throws Exception {
        Session c = readyCustomer();
        String id = submittedApplication(c, "300000.00", 36);
        perform(authed(json(post("/api/v1/applications/" + id + "/submit"), "{\"declarationAccepted\":true}"), c)
                .header("X-Correlation-Id", "c-test-correlation-1"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Correlation-Id", "c-test-correlation-1"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message").value("The requested action is not valid for the current state."))
                .andExpect(jsonPath("$.correlationId").value("c-test-correlation-1"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
        MvcResult notFound = perform(authed(get("/api/v1/applications/" + UUID.randomUUID()), c))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(body(notFound)).doesNotContain("Exception").doesNotContain("at com.");
        perform(authed(json(post("/api/v1/applications"), "{not json"), c))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void healthEndpointIsPublicButBusinessEndpointsAreNot() throws Exception {
        perform(get("/actuator/health")).andExpect(status().isOk());
        perform(get("/api/v1/loan-products")).andExpect(status().isUnauthorized());
    }
}
