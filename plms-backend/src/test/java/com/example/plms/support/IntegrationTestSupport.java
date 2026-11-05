package com.example.plms.support;

import com.example.plms.common.CorrelationIdFilter;
import com.example.plms.product.LoanProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test base: real Spring context, real security filter chain, H2 in MySQL mode,
 * synthetic seed data. Each test creates its own customers so tests stay independent.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    public static final String STAFF_PASSWORD = "Test-Staff-2026";
    public static final String CUSTOMER_PASSWORD = "Customer-Pass-1";
    public static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected CorrelationIdFilter correlationIdFilter;
    @Autowired
    protected LoanProductRepository productRepository;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = newMockMvc();
    }

    protected MockMvc newMockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(correlationIdFilter)
                .apply(springSecurity())
                .build();
    }

    // ------------------------------------------------------------------ generic helpers

    public record Session(String userId, String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected static String read(MvcResult result, String path) {
        Object value = JsonPath.read(body(result), path);
        return value == null ? null : String.valueOf(value);
    }

    protected static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResultActions perform(RequestBuilder request) throws Exception {
        return mvc.perform(request);
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, Session session) {
        return builder.header("Authorization", session.bearer());
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String json) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    protected String productId(String code) {
        return productRepository.findByCode(code).orElseThrow().getId();
    }

    // ------------------------------------------------------------------ auth helpers

    protected Session login(String email, String password) throws Exception {
        MvcResult r = perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session(read(r, "$.user.id"), email, read(r, "$.accessToken"), read(r, "$.refreshToken"));
    }

    protected Session staff(String localPart) throws Exception {
        return login(localPart + "@plms.local", STAFF_PASSWORD);
    }

    protected Session registerCustomer() throws Exception {
        return registerCustomer(uniqueEmail("customer"));
    }

    protected Session registerCustomer(String email) throws Exception {
        perform(json(post("/api/v1/auth/register"), """
                {"fullName":"Synthetic Customer","email":"%s","mobile":"+91 98765 43210",
                 "password":"%s","acceptedTerms":true}""".formatted(email, CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated());
        return login(email, CUSTOMER_PASSWORD);
    }

    /** Registered customer with a complete profile (age about 30, salaried). */
    protected Session readyCustomer() throws Exception {
        Session c = registerCustomer();
        perform(authed(json(put("/api/v1/customers/me"), """
                {"fullName":"Synthetic Customer","dateOfBirth":"1996-05-10","phone":"+91 98765 43210",
                 "addressLine1":"12 Test Street","city":"Pune","state":"MH","postalCode":"411001",
                 "employmentType":"SALARIED","employerName":"Example Corp","employmentStartDate":"2020-01-01",
                 "occupation":"Engineer","monthlyIncome":85000.00,"existingMonthlyObligations":12000.00}"""), c))
                .andExpect(status().isOk());
        return c;
    }

    // ------------------------------------------------------------------ application helpers

    protected String draftJson(String productId, String amount, int months, String income) {
        return """
                {"productId":"%s","requestedAmount":%s,"requestedMonths":%d,"purpose":"Home improvement",
                 "employmentType":"SALARIED","employerName":"Example Corp","employmentStartDate":"2020-01-01",
                 "monthlyIncome":%s,"existingMonthlyObligations":12000.00,"monthlyExpenses":20000.00}"""
                .formatted(productId, amount, months, income);
    }

    protected String createDraft(Session customer, String amount, int months) throws Exception {
        MvcResult r = perform(authed(json(post("/api/v1/applications"),
                draftJson(productId("PERSONAL-FLEX"), amount, months, "85000.00")), customer))
                .andExpect(status().isCreated()).andReturn();
        return read(r, "$.applicationId");
    }

    protected MvcResult submit(Session customer, String applicationId) throws Exception {
        return perform(authed(json(post("/api/v1/applications/" + applicationId + "/submit"),
                "{\"declarationAccepted\":true}"), customer)).andReturn();
    }

    /** Draft + submit; returns the application id (status UNDER_REVIEW for an eligible profile). */
    protected String submittedApplication(Session customer, String amount, int months) throws Exception {
        String id = createDraft(customer, amount, months);
        MvcResult r = submit(customer, id);
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Submit failed: " + body(r));
        }
        return id;
    }

    protected String upload(Session customer, String applicationId, String category, String filename, byte[] content)
            throws Exception {
        MvcResult r = perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", content))
                .param("applicationId", applicationId).param("category", category)
                .header("Authorization", customer.bearer()))
                .andExpect(status().isCreated()).andReturn();
        return read(r, "$.documentId");
    }

    protected void verifyDocument(Session verifier, String documentId) throws Exception {
        perform(authed(json(post("/api/v1/documents/" + documentId + "/decision"),
                "{\"decision\":\"VERIFIED\",\"reasonCode\":\"DOCUMENT_OK\"}"), verifier))
                .andExpect(status().isOk());
    }

    protected void uploadAndVerifyMandatoryDocuments(Session customer, String applicationId) throws Exception {
        Session verifier = staff("verifier");
        verifyDocument(verifier, upload(customer, applicationId, "IDENTITY_PROOF", "id.pdf", PDF));
        verifyDocument(verifier, upload(customer, applicationId, "INCOME_PROOF", "salary.pdf", PDF));
    }

    protected void recommend(Session maker, String applicationId) throws Exception {
        perform(authed(json(post("/api/v1/applications/" + applicationId + "/recommendation"),
                "{\"recommendation\":\"APPROVE\",\"reasonCode\":\"POLICY_MET\",\"comment\":\"Checks complete\"}"),
                maker)).andExpect(status().isCreated());
    }

    protected MvcResult decide(Session approver, String applicationId, String decision, String amount,
                               Integer months) throws Exception {
        String amountJson = amount == null ? "" : ",\"approvedAmount\":" + amount;
        String monthsJson = months == null ? "" : ",\"approvedMonths\":" + months;
        return perform(authed(json(post("/api/v1/applications/" + applicationId + "/decision"),
                "{\"decision\":\"" + decision + "\",\"reasonCode\":\"POLICY_MET\",\"comment\":\"Reviewed\""
                        + amountJson + monthsJson + "}"), approver)).andReturn();
    }

    public record ApprovedLoan(Session customer, String applicationId, String loanAccountId) {
    }

    /** Full path to an APPROVED application with a loan account awaiting disbursement. */
    protected ApprovedLoan approvedLoan(String amount, int months) throws Exception {
        Session customer = readyCustomer();
        String appId = submittedApplication(customer, amount, months);
        uploadAndVerifyMandatoryDocuments(customer, appId);
        recommend(staff("officer"), appId);
        MvcResult decision = decide(staff("approver"), appId, "APPROVE", amount, months);
        if (decision.getResponse().getStatus() != 200) {
            throw new AssertionError("Approval failed: " + body(decision));
        }
        return new ApprovedLoan(customer, appId, read(decision, "$.loanAccountId"));
    }

    protected MvcResult disburse(Session finance, String accountId, String key, String gross, String fee,
                                 String simulatedOutcome) throws Exception {
        String outcome = simulatedOutcome == null ? "" : ",\"simulatedOutcome\":\"" + simulatedOutcome + "\"";
        return perform(authed(json(post("/api/v1/loan-accounts/" + accountId + "/disbursements")
                .header("Idempotency-Key", key), "{\"grossAmount\":" + gross + ",\"feeAmount\":" + fee + outcome
                + "}"), finance)).andReturn();
    }

    /** Approved and successfully disbursed: account ACTIVE with a generated schedule. */
    protected ApprovedLoan activeLoan(String amount, int months) throws Exception {
        ApprovedLoan loan = approvedLoan(amount, months);
        MvcResult r = disburse(staff("finance"), loan.loanAccountId(), "disb-" + UUID.randomUUID(), amount, "0.00",
                null);
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError("Disbursement failed: " + body(r));
        }
        return loan;
    }

    protected MvcResult pay(Session customer, String accountId, int installmentNo, String amount, String method,
                            String key) throws Exception {
        return perform(authed(json(post("/api/v1/repayments").header("Idempotency-Key", key), """
                {"loanAccountId":"%s","installmentNo":%d,"amount":%s,"paymentMethod":"%s"}"""
                .formatted(accountId, installmentNo, amount, method)), customer)).andReturn();
    }

    protected String installmentTotal(Session customer, String accountId, int installmentNo) throws Exception {
        MvcResult r = perform(authed(get("/api/v1/loan-accounts/" + accountId + "/schedule"), customer))
                .andExpect(status().isOk()).andReturn();
        return read(r, "$.installments[" + (installmentNo - 1) + "].total");
    }
}
