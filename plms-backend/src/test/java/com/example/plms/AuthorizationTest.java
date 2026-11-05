package com.example.plms;

import com.example.plms.audit.AuditEventRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Authorization: ownership, role checks and no data leakage. */
class AuthorizationTest extends IntegrationTestSupport {

    @Autowired
    AuditEventRepository audit;

    @Test
    void customerCannotReadAnotherCustomersApplication() throws Exception {
        Session a = readyCustomer();
        Session b = readyCustomer();
        String appOfB = createDraft(b, "300000.00", 36);

        MvcResult r = perform(authed(get("/api/v1/applications/" + appOfB), a))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn();
        assertThat(body(r)).doesNotContain(appOfB).doesNotContain("requestedAmount");
        perform(authed(get("/api/v1/applications/" + appOfB + "/history"), a)).andExpect(status().isNotFound());
        assertThat(audit.findByEntityIdOrderByOccurredAtAsc(appOfB))
                .anyMatch(e -> e.getAction().equals("ACCESS_DENIED") && a.userId().equals(e.getActorId()));
    }

    @Test
    void customerCannotModifyOrSubmitAnotherCustomersApplication() throws Exception {
        Session a = readyCustomer();
        Session b = readyCustomer();
        String appOfB = createDraft(b, "300000.00", 36);
        assertThat(submit(a, appOfB).getResponse().getStatus()).isEqualTo(404);
        perform(authed(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/applications/" + appOfB), "{\"purpose\":\"Hijack\"}"), a))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerListSeesOnlyOwnApplications() throws Exception {
        Session a = readyCustomer();
        Session b = readyCustomer();
        createDraft(a, "300000.00", 36);
        createDraft(b, "200000.00", 24);
        perform(authed(get("/api/v1/applications"), a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestedAmount").value(300000.0));
    }

    @Test
    void customerCannotUseStaffEndpoints() throws Exception {
        Session c = readyCustomer();
        perform(authed(get("/api/v1/staff/applications?queue=REVIEW"), c)).andExpect(status().isForbidden());
        perform(authed(get("/api/v1/reports/overdue"), c)).andExpect(status().isForbidden());
        perform(authed(get("/api/v1/audit-events"), c)).andExpect(status().isForbidden());
        perform(authed(get("/api/v1/admin/users"), c)).andExpect(status().isForbidden());
        perform(authed(json(post("/api/v1/credit-assessments"), "{\"applicationId\":\"x\"}"), c))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void staffCannotActOutsideTheirRole() throws Exception {
        ApprovedLoan loan = approvedLoan("300000.00", 36);
        // Admin has no routine approval authority; verifier cannot disburse; officer cannot verify documents.
        String otherApp = submittedApplication(readyCustomer(), "300000.00", 36);
        assertThat(decide(staff("admin"), otherApp, "APPROVE", null, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(disburse(staff("verifier"), loan.loanAccountId(), "k-verifier", "300000.00", "0.00", null)
                .getResponse().getStatus()).isEqualTo(403);
        perform(authed(json(post("/api/v1/documents/does-not-matter/decision"),
                "{\"decision\":\"VERIFIED\"}"), staff("officer"))).andExpect(status().isForbidden());
    }

    @Test
    void customerCannotSeeAnotherCustomersLoanAccountOrSchedule() throws Exception {
        ApprovedLoan loan = activeLoan("300000.00", 36);
        Session intruder = readyCustomer();
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId()), intruder)).andExpect(status().isNotFound());
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/schedule"), intruder))
                .andExpect(status().isNotFound());
        perform(authed(get("/api/v1/loan-accounts/" + loan.loanAccountId() + "/repayments"), intruder))
                .andExpect(status().isNotFound());
        String total = installmentTotal(loan.customer(), loan.loanAccountId(), 1);
        assertThat(pay(intruder, loan.loanAccountId(), 1, total, "SIMULATED", "intruder-pay").getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void staffQueueRowsDoNotExposeFullCustomerPii() throws Exception {
        submittedApplication(readyCustomer(), "300000.00", 36);
        MvcResult r = perform(authed(get("/api/v1/staff/applications?queue=REVIEW&size=5"), staff("officer")))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(r)).doesNotContain("Test Street").doesNotContain("98765").contains("customerRef");
    }
}
