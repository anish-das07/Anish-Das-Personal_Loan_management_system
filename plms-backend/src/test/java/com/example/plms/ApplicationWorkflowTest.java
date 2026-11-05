package com.example.plms;

import com.example.plms.application.LoanApplicationRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Application: validation, product rules, snapshots, idempotent submission, state machine. */
class ApplicationWorkflowTest extends IntegrationTestSupport {

    @Autowired
    LoanApplicationRepository applications;

    @Test
    void createsDraftOwnedByTheAuthenticatedCustomer() throws Exception {
        Session c = readyCustomer();
        MvcResult r = perform(authed(json(post("/api/v1/applications"),
                draftJson(productId("PERSONAL-FLEX"), "350000.00", 36, "85000.00")), c))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.message").value("Application draft created"))
                .andExpect(jsonPath("$.nextAction").value("Complete required sections and submit"))
                .andReturn();
        assertThat(applications.findById(read(r, "$.applicationId")).orElseThrow().getCustomerId())
                .isEqualTo(c.userId());
    }

    @Test
    void createWithSameIdempotencyKeyReturnsTheSameDraft() throws Exception {
        Session c = readyCustomer();
        String key = "app-create-" + UUID.randomUUID();
        String body = draftJson(productId("PERSONAL-FLEX"), "300000.00", 36, "85000.00");
        String first = read(perform(authed(json(post("/api/v1/applications").header("Idempotency-Key", key), body), c))
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        String second = read(perform(authed(json(post("/api/v1/applications").header("Idempotency-Key", key), body), c))
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        Session c = readyCustomer();
        perform(authed(json(post("/api/v1/applications"), """
                {"productId":"%s","requestedAmount":-5,"requestedMonths":0,"purpose":""}"""
                .formatted(productId("PERSONAL-FLEX"))), c))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void amountOutsideProductLimitsIsRejected() throws Exception {
        Session c = readyCustomer();
        perform(authed(json(post("/api/v1/applications"),
                draftJson(productId("PERSONAL-FLEX"), "5000000.00", 36, "85000.00")), c))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUTSIDE_PRODUCT_LIMITS"));
    }

    @Test
    void submitWithMissingIncomeIsRejectedAndNothingIsCommitted() throws Exception {
        Session c = readyCustomer();
        MvcResult created = perform(authed(json(post("/api/v1/applications"), """
                {"productId":"%s","requestedAmount":300000.00,"requestedMonths":36,"purpose":"Home improvement",
                 "employmentType":"SALARIED"}""".formatted(productId("PERSONAL-FLEX"))), c))
                .andExpect(status().isCreated()).andReturn();
        String id = read(created, "$.applicationId");

        MvcResult r = submit(c, id);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(read(r, "$.fieldErrors[0].field")).isEqualTo("monthlyIncome");
        perform(authed(get("/api/v1/applications/" + id), c)).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.submittedAt").doesNotExist());
    }

    @Test
    void submitRequiresDeclarationAndCompleteProfile() throws Exception {
        Session c = readyCustomer();
        String id = createDraft(c, "300000.00", 36);
        perform(authed(json(post("/api/v1/applications/" + id + "/submit"), "{\"declarationAccepted\":false}"), c))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECLARATION_REQUIRED"));

        Session incomplete = registerCustomer();
        String draft = createDraft(incomplete, "300000.00", 36);
        assertThat(read(submit(incomplete, draft), "$.code")).isEqualTo("PROFILE_INCOMPLETE");
    }

    @Test
    void inactiveProductCannotReceiveNewApplicationsOrSubmissions() throws Exception {
        Session admin = staff("admin");
        String code = "TEST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String productJson = """
                {"code":"%s","name":"Temp Product","purpose":"Testing","minAmount":50000.00,"maxAmount":500000.00,
                 "minMonths":6,"maxMonths":60,"annualRate":12.00,"processingFeePercent":1.00,
                 "activeFrom":"2026-01-01","active":%s,"policyVersion":"PERSONAL-2026.1"}""";
        String productId = read(perform(authed(json(post("/api/v1/admin/products"), productJson.formatted(code, true)),
                admin)).andExpect(status().isCreated()).andReturn(), "$.id");

        Session c = readyCustomer();
        String draft = read(perform(authed(json(post("/api/v1/applications"),
                draftJson(productId, "300000.00", 36, "85000.00")), c))
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");

        perform(authed(json(put("/api/v1/admin/products/" + productId), productJson.formatted(code, false)), admin))
                .andExpect(status().isOk());

        assertThat(read(submit(c, draft), "$.code")).isEqualTo("PRODUCT_NOT_AVAILABLE");
        perform(authed(json(post("/api/v1/applications"), draftJson(productId, "300000.00", 36, "85000.00")), c))
                .andExpect(status().isConflict());
        // Inactive products are hidden from the customer catalogue.
        MvcResult catalogue = perform(authed(get("/api/v1/loan-products?size=100"), c)).andReturn();
        assertThat(body(catalogue)).doesNotContain(code);
    }

    @Test
    void submissionSnapshotsTermsSoLaterProductEditsDoNotChangeTheApplication() throws Exception {
        Session admin = staff("admin");
        String code = "SNAP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String productJson = """
                {"code":"%s","name":"Snapshot Product","purpose":"Testing","minAmount":50000.00,"maxAmount":900000.00,
                 "minMonths":6,"maxMonths":60,"annualRate":%s,"processingFeePercent":1.00,
                 "activeFrom":"2026-01-01","active":true,"policyVersion":"PERSONAL-2026.1"}""";
        String productId = read(perform(authed(json(post("/api/v1/admin/products"), productJson.formatted(code, "12.00")),
                admin)).andExpect(status().isCreated()).andReturn(), "$.id");
        Session c = readyCustomer();
        String app = read(perform(authed(json(post("/api/v1/applications"),
                draftJson(productId, "300000.00", 36, "85000.00")), c))
                .andExpect(status().isCreated()).andReturn(), "$.applicationId");
        assertThat(submit(c, app).getResponse().getStatus()).isEqualTo(200);

        perform(authed(json(put("/api/v1/admin/products/" + productId), productJson.formatted(code, "18.00")), admin))
                .andExpect(status().isOk());

        var stored = applications.findById(app).orElseThrow();
        assertThat(stored.getSnapshotAnnualRate()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(stored.getTermsSnapshot()).contains("\"annualRate\":12.00").contains(code);
        assertThat(stored.getPolicyVersion()).isEqualTo("PERSONAL-2026.1");
    }

    @Test
    void duplicateSubmitWithSameKeyReturnsExistingResultAndOtherwiseIs409() throws Exception {
        Session c = readyCustomer();
        String id = createDraft(c, "300000.00", 36);
        String key = "app-submit-" + UUID.randomUUID();
        perform(authed(json(post("/api/v1/applications/" + id + "/submit").header("Idempotency-Key", key),
                "{\"declarationAccepted\":true}"), c))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        perform(authed(json(post("/api/v1/applications/" + id + "/submit").header("Idempotency-Key", key),
                "{\"declarationAccepted\":true}"), c))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        perform(authed(json(post("/api/v1/applications/" + id + "/submit"), "{\"declarationAccepted\":true}"), c))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void submittedApplicationCannotBeEditedAndHistoryIsRecorded() throws Exception {
        Session c = readyCustomer();
        String id = submittedApplication(c, "300000.00", 36);
        perform(authed(json(patch("/api/v1/applications/" + id), "{\"purpose\":\"Changed\"}"), c))
                .andExpect(status().isConflict());
        perform(authed(get("/api/v1/applications/" + id + "/history"), c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[2].toStatus").value("UNDER_REVIEW"));
    }

    @Test
    void draftCanBeEditedAndWithdrawnButWithdrawnIsTerminal() throws Exception {
        Session c = readyCustomer();
        String id = createDraft(c, "300000.00", 36);
        perform(authed(json(patch("/api/v1/applications/" + id), "{\"requestedAmount\":250000.00}"), c))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestedAmount").value(250000.0));
        perform(authed(post("/api/v1/applications/" + id + "/withdraw"), c))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(submit(c, id).getResponse().getStatus()).isEqualTo(409);
    }
}
