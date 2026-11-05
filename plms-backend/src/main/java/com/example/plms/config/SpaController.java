package com.example.plms.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page app (packaged into /static by the Maven build). Browser routes such as
 * /app/dashboard or /staff/approvals/queue are forwarded to index.html so a page refresh works; React Router
 * then renders the page. API routes (/api/**) are never matched here.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/register", "/forgot-password", "/reset-password", "/forbidden",
            "/app", "/app/**", "/staff", "/staff/**", "/admin", "/admin/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
