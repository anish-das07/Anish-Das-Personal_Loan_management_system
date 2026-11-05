package com.example.plms;

import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Eligibility: rule boundaries, versioned policy, rule-level results, safe customer view. */
class EligibilityTest extends IntegrationTestSupport {

    private String submit(Session c, String amount, int months, String income, String obligations) throws Exception {
        String id = read(perform(authed(json(post("/api/v1/applications"), """
                {"productId":"%s","requestedAmount":%s,"requestedMonths":%d,"purpose":"Home improvement",
                 "employmentType":"SALARIED","employmentStartDate":"2020-01-01",
                 "monthlyIncome":%s,"existingMonthlyObligations":%s}"""
                .formatted(productId("PERSONAL-FLEX"), amount, months, income, obligations)), c))
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        assertThat(submit(c, id).getResponse().getStatus()).isEqualTo(200);
        return id;
    }

    @Test
    void incomeExactlyAtTheMinimumPassesAndOnePaisaBelowFails() throws Exception {
        Session atMin = readyCustomer();
        String ok = submit(atMin, "50000.00", 12, "25000.00", "0.00");
        perform(authed(get("/api/v1/applications/" + ok), atMin))
                .andExpect(jsonPath("$.eligibilityOutcome").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        Session below = readyCustomer();
        String rejected = submit(below, "50000.00", 12, "24999.99", "0.00");
        perform(authed(get("/api/v1/applications/" + rejected), below))
                .andExpect(jsonPath("$.eligibilityOutcome").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusLabel").value("Not approved"));
    }

    @Test
    void storesPolicyVersionAndEveryRuleResultWithReasons() throws Exception {
        Session c = readyCustomer();
        String id = submit(c, "300000.00", 36, "85000.00", "12000.00");
        perform(authed(get("/api/v1/applications/" + id + "/eligibility"), staff("analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value("PERSONAL-2026.1"))
                .andExpect(jsonPath("$.outcome").value("ELIGIBLE"))
                .andExpect(jsonPath("$.rules.length()").value(6))
                .andExpect(jsonPath("$.rules[0].code").value("PRODUCT_RANGE"))
                .andExpect(jsonPath("$.rules[2].code").value("AFFORDABILITY"))
                .andExpect(jsonPath("$.estimatedEmi").value(9964.29))
                .andExpect(jsonPath("$.debtToIncomeRatio").value(0.2584))
                .andExpect(jsonPath("$.evaluatedAt").exists());
    }

    @Test
    void affordabilityInReviewBandRoutesToManualReviewNotAutoApproval() throws Exception {
        Session c = readyCustomer();
        String id = submit(c, "600000.00", 36, "85000.00", "25000.00");
        perform(authed(get("/api/v1/applications/" + id), c))
                .andExpect(jsonPath("$.eligibilityOutcome").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void customerSeesOnlyTheSafeSummary() throws Exception {
        Session c = readyCustomer();
        String id = submit(c, "300000.00", 36, "85000.00", "12000.00");
        perform(authed(get("/api/v1/applications/" + id + "/eligibility"), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ELIGIBLE"))
                .andExpect(jsonPath("$.passed").value(6))
                .andExpect(jsonPath("$.estimatedEmi").doesNotExist())
                .andExpect(jsonPath("$.debtToIncomeRatio").doesNotExist());
    }

    @Test
    void newPolicyVersionChangesOutcomeAndKeepsHistory() throws Exception {
        String version = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        perform(authed(json(post("/api/v1/admin/eligibility-policies"), """
                {"policyVersion":"%s","minMonthlyIncome":100000.00,"passMaxDti":0.40,"reviewMaxDti":0.55,
                 "minEmploymentMonths":6,"allowedEmploymentTypes":["SALARIED"],"minAge":21,"maxAge":60,
                 "creditRequired":true,"creditRejectBelow":600,"creditReviewBelow":650,"autoRejectOnFail":true}"""
                .formatted(version)), staff("admin"))).andExpect(status().isCreated());
        // Policy versions are immutable.
        perform(authed(json(post("/api/v1/admin/eligibility-policies"), """
                {"policyVersion":"%s","minMonthlyIncome":1.00,"passMaxDti":0.40,"reviewMaxDti":0.55,
                 "minEmploymentMonths":6,"allowedEmploymentTypes":["SALARIED"],"minAge":21,"maxAge":60,
                 "creditRequired":true,"creditRejectBelow":600,"creditReviewBelow":650,"autoRejectOnFail":true}"""
                .formatted(version)), staff("admin"))).andExpect(status().isConflict());

        Session c = readyCustomer();
        String id = submit(c, "300000.00", 36, "85000.00", "12000.00");
        MvcResult r = perform(authed(json(post("/api/v1/eligibility/evaluations"),
                "{\"applicationId\":\"" + id + "\",\"policyVersion\":\"" + version + "\"}"), staff("analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.policyVersion").value(version))
                .andReturn();
        assertThat(read(r, "$.rules[1].result")).isEqualTo("FAIL");

        // Re-running the original policy gives the same deterministic result as before.
        perform(authed(json(post("/api/v1/eligibility/evaluations"),
                "{\"applicationId\":\"" + id + "\",\"policyVersion\":\"PERSONAL-2026.1\"}"), staff("analyst")))
                .andExpect(jsonPath("$.outcome").value("ELIGIBLE"));
    }

    @Test
    void eligibilityIsNotApproval() throws Exception {
        Session c = readyCustomer();
        String id = submit(c, "300000.00", 36, "85000.00", "12000.00");
        perform(authed(get("/api/v1/applications/" + id), c))
                .andExpect(jsonPath("$.eligibilityOutcome").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }
}
