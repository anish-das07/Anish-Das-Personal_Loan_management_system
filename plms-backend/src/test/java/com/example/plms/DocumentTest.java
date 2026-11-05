package com.example.plms;

import com.example.plms.audit.AuditEventRepository;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Documents: size/type/content checks, private storage, versioned resubmission, reviewer rules. */
class DocumentTest extends IntegrationTestSupport {

    @Autowired
    AuditEventRepository audit;

    private MvcResult uploadRaw(Session c, String appId, String category, String filename, byte[] content)
            throws Exception {
        return perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", content))
                .param("applicationId", appId).param("category", category)
                .header("Authorization", c.bearer())).andReturn();
    }

    @Test
    void validUploadIsStoredPrivatelyAndPendingReview() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        MvcResult r = uploadRaw(c, app, "INCOME_PROOF", "../../salary slip.pdf", PDF);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(r, "$.status")).isEqualTo("PENDING_REVIEW");
        assertThat(read(r, "$.version")).isEqualTo("1");
        assertThat(read(r, "$.fileName")).isEqualTo("salary_slip.pdf");
        assertThat(read(r, "$.mimeType")).isEqualTo("application/pdf");
        assertThat(body(r)).doesNotContain("objectKey").doesNotContain("data/documents");
    }

    @Test
    void oversizedFileIsRejectedWith413() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        MvcResult r = uploadRaw(c, app, "INCOME_PROOF", "big.pdf", big);
        assertThat(r.getResponse().getStatus()).isEqualTo(413);
        assertThat(read(r, "$.code")).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void disallowedTypeIsRejectedWith415() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        MvcResult r = uploadRaw(c, app, "INCOME_PROOF", "tool.exe", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(r.getResponse().getStatus()).isEqualTo(415);
    }

    @Test
    void corruptFileWhoseContentDoesNotMatchItsTypeIsRejected() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        // Browser says application/pdf and the name ends in .pdf, but the bytes are not a PDF.
        MvcResult r = uploadRaw(c, app, "INCOME_PROOF", "fake.pdf", "<html>not a pdf</html>".getBytes());
        assertThat(r.getResponse().getStatus()).isEqualTo(415);
        assertThat(read(r, "$.code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void passwordProtectedAndEmptyFilesAreRejected() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        byte[] encrypted = "%PDF-1.7\ntrailer << /Encrypt 5 0 R >>".getBytes(StandardCharsets.US_ASCII);
        assertThat(read(uploadRaw(c, app, "INCOME_PROOF", "locked.pdf", encrypted), "$.code"))
                .isEqualTo("PASSWORD_PROTECTED_FILE");
        assertThat(uploadRaw(c, app, "INCOME_PROOF", "empty.pdf", new byte[0]).getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void customerCannotUploadToAnotherCustomersApplication() throws Exception {
        Session owner = readyCustomer();
        String app = submittedApplication(owner, "300000.00", 36);
        assertThat(uploadRaw(readyCustomer(), app, "INCOME_PROOF", "x.pdf", PDF).getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void resubmissionRequiresReasonAndCreatesANewVersion() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        String v1 = upload(c, app, "INCOME_PROOF", "salary.pdf", PDF);
        Session verifier = staff("verifier");

        perform(authed(json(post("/api/v1/documents/" + v1 + "/decision"),
                "{\"decision\":\"RESUBMISSION_REQUIRED\"}"), verifier))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        perform(authed(json(post("/api/v1/documents/" + v1 + "/decision"),
                "{\"decision\":\"RESUBMISSION_REQUIRED\",\"reasonCode\":\"IMAGE_UNREADABLE\","
                        + "\"comment\":\"Upload a clearer, complete copy.\"}"), verifier))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESUBMISSION_REQUIRED"));
        // A decided version cannot be decided again.
        perform(authed(json(post("/api/v1/documents/" + v1 + "/decision"), "{\"decision\":\"VERIFIED\"}"), verifier))
                .andExpect(status().isConflict());

        perform(authed(get("/api/v1/applications/" + app + "/documents/checklist"), c))
                .andExpect(jsonPath("$[?(@.category=='INCOME_PROOF')].status").value("RESUBMISSION_REQUIRED"))
                .andExpect(jsonPath("$[?(@.category=='INCOME_PROOF')].reviewerComment")
                        .value("Upload a clearer, complete copy."));

        String v2 = upload(c, app, "INCOME_PROOF", "salary-clear.pdf", PDF);
        verifyDocument(verifier, v2);
        perform(authed(get("/api/v1/applications/" + app + "/documents"), c))
                .andExpect(jsonPath("$[?(@.documentId=='" + v2 + "')].version").value(2))
                .andExpect(jsonPath("$[?(@.documentId=='" + v2 + "')].status").value("VERIFIED"))
                .andExpect(jsonPath("$[?(@.documentId=='" + v1 + "')].status").value("SUPERSEDED"));
        perform(authed(get("/api/v1/documents/" + v1 + "/verifications"), verifier))
                .andExpect(jsonPath("$[0].reasonCode").value("IMAGE_UNREADABLE"))
                .andExpect(jsonPath("$[0].verifierId").exists());
    }

    @Test
    void documentReadsAreAuthorisedServedAsAttachmentAndLogged() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        String doc = upload(c, app, "IDENTITY_PROOF", "id.pdf", PDF);

        perform(authed(get("/api/v1/documents/" + doc + "/content"), c))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        perform(authed(get("/api/v1/documents/" + doc + "/content"), staff("verifier"))).andExpect(status().isOk());
        perform(authed(get("/api/v1/documents/" + doc + "/content"), readyCustomer())).andExpect(status().isNotFound());
        perform(authed(get("/api/v1/documents/" + doc + "/content"), staff("finance"))).andExpect(status().isNotFound());
        perform(get("/api/v1/documents/" + doc + "/content")).andExpect(status().isUnauthorized());

        assertThat(audit.findByEntityIdOrderByOccurredAtAsc(doc))
                .extracting(com.example.plms.audit.AuditEvent::getAction)
                .contains("DOCUMENT_UPLOADED", "DOCUMENT_READ", "DOCUMENT_ACCESS_DENIED");
    }

    @Test
    void verifierQueueListsPendingDocuments() throws Exception {
        Session c = readyCustomer();
        String app = submittedApplication(c, "300000.00", 36);
        String doc = upload(c, app, "BANK_STATEMENT", "statement.pdf", PDF);
        MvcResult r = perform(authed(get("/api/v1/staff/documents?status=PENDING_REVIEW&size=100"), staff("verifier")))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(r)).contains(doc);
    }
}
