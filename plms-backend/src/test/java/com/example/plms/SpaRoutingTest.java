package com.example.plms;

import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Spring Boot app serves the React UI: client routes forward to index.html; the API stays protected. */
class SpaRoutingTest extends IntegrationTestSupport {

    @Test
    void browserRoutesForwardToTheReactApp() throws Exception {
        for (String path : new String[]{"/", "/login", "/app/dashboard", "/app/applications/123", "/staff/approvals/queue",
                "/admin/users"}) {
            perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiRoutesAreNotForwardedAndStillRequireAuthentication() throws Exception {
        perform(get("/api/v1/applications")).andExpect(status().isUnauthorized());
    }
}
