package com.example.plms;

import com.example.plms.credit.CreditAssessment;
import com.example.plms.credit.CreditAssessmentService;
import com.example.plms.credit.SyntheticCreditBureau;
import com.example.plms.support.IntegrationTestSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Credit adapter and circuit breaker: timeout, malformed response, bounded retries, fail fast. */
class CreditAdapterTest extends IntegrationTestSupport {

    @Autowired
    SyntheticCreditBureau bureau;
    @Autowired
    CreditAssessmentService creditService;

    @BeforeEach
    void normalProvider() {
        bureau.setMode(SyntheticCreditBureau.Mode.NORMAL);
        creditService.circuitBreaker().reset();
    }

    @AfterEach
    void restoreProvider() {
        bureau.setMode(SyntheticCreditBureau.Mode.NORMAL);
        creditService.circuitBreaker().reset();
    }

    @Test
    void providerTimeoutLeadsToPendingReviewNotAFailedScore() throws Exception {
        bureau.setMode(SyntheticCreditBureau.Mode.TIMEOUT);
        Session c = readyCustomer();
        String id = submittedApplication(c, "300000.00", 36);

        perform(authed(get("/api/v1/applications/" + id), c))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.eligibilityOutcome").value("PENDING_PROVIDER"));
        perform(authed(get("/api/v1/credit-assessments?applicationId=" + id), staff("analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_PROVIDER"))
                .andExpect(jsonPath("$[0].score").doesNotExist())
                .andExpect(jsonPath("$[0].failureReason").value("TIMEOUT"));
        perform(authed(get("/api/v1/applications/" + id + "/eligibility"), staff("analyst")))
                .andExpect(jsonPath("$.rules[5].code").value("CREDIT_DATA"))
                .andExpect(jsonPath("$.rules[5].result").value("PENDING"));
    }

    @Test
    void malformedResponseIsNeverTurnedIntoAScore() {
        bureau.setMode(SyntheticCreditBureau.Mode.MALFORMED);
        CreditAssessment a = creditService.assess("app-" + UUID.randomUUID(), "cust-" + UUID.randomUUID(), "test");
        assertThat(a.getStatus()).isEqualTo("PENDING_PROVIDER");
        assertThat(a.getScore()).isNull();
        assertThat(a.getFailureReason()).isEqualTo("MALFORMED_RESPONSE");
    }

    @Test
    void transientFailuresAreRetriedAFixedNumberOfTimes() {
        bureau.setMode(SyntheticCreditBureau.Mode.UNAVAILABLE);
        int before = bureau.callCount();
        CreditAssessment a = creditService.assess("app-" + UUID.randomUUID(), "cust-x", "test");
        assertThat(bureau.callCount() - before).isEqualTo(2); // retry-attempts = 2, then give up
        assertThat(a.getStatus()).isEqualTo("PENDING_PROVIDER");
    }

    @Test
    void malformedResponsesAreNotRetried() {
        bureau.setMode(SyntheticCreditBureau.Mode.MALFORMED);
        int before = bureau.callCount();
        creditService.assess("app-" + UUID.randomUUID(), "cust-x", "test");
        assertThat(bureau.callCount() - before).isEqualTo(1);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndCallersFailFast() {
        bureau.setMode(SyntheticCreditBureau.Mode.UNAVAILABLE);
        creditService.assess("app-1-" + UUID.randomUUID(), "cust", "test"); // 2 failed calls
        creditService.assess("app-2-" + UUID.randomUUID(), "cust", "test"); // 4 failed calls -> window full
        assertThat(creditService.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        bureau.setMode(SyntheticCreditBureau.Mode.NORMAL);
        int before = bureau.callCount();
        CreditAssessment fastFail = creditService.assess("app-3-" + UUID.randomUUID(), "cust", "test");
        assertThat(fastFail.getFailureReason()).isEqualTo("CIRCUIT_OPEN");
        assertThat(fastFail.getScore()).isNull();
        assertThat(bureau.callCount()).isEqualTo(before); // the provider was not called
    }

    @Test
    void analystCanRerunAssessmentAfterProviderRecovers() throws Exception {
        bureau.setMode(SyntheticCreditBureau.Mode.UNAVAILABLE);
        Session c = readyCustomer();
        String id = submittedApplication(c, "300000.00", 36);
        perform(authed(get("/api/v1/applications/" + id), c))
                .andExpect(jsonPath("$.eligibilityOutcome").value("PENDING_PROVIDER"));

        bureau.setMode(SyntheticCreditBureau.Mode.NORMAL);
        creditService.circuitBreaker().reset();
        perform(authed(json(post("/api/v1/eligibility/evaluations"), "{\"applicationId\":\"" + id + "\"}"),
                staff("analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ELIGIBLE"));
        perform(authed(get("/api/v1/applications/" + id), c))
                .andExpect(jsonPath("$.eligibilityOutcome").value("ELIGIBLE"));
    }

    @Test
    void customerSeesBandButNotRawScore() throws Exception {
        Session c = readyCustomer();
        String id = submittedApplication(c, "300000.00", 36);
        perform(authed(get("/api/v1/credit-assessments?applicationId=" + id), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].scoreBand").exists())
                .andExpect(jsonPath("$[0].score").doesNotExist())
                .andExpect(jsonPath("$[0].source").value("SYNTHETIC-BUREAU (synthetic)"));
    }

    @Test
    void adminCanSwitchSimulatedProviderMode() throws Exception {
        perform(authed(json(put("/api/v1/admin/simulations/credit-provider"), "{\"mode\":\"TIMEOUT\"}"), staff("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("TIMEOUT"));
        assertThat(bureau.mode()).isEqualTo(SyntheticCreditBureau.Mode.TIMEOUT);
    }
}
