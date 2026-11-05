package com.example.plms;

import com.example.plms.audit.AuditEventRepository;
import com.example.plms.auth.UserAccount;
import com.example.plms.auth.UserAccountRepository;
import com.example.plms.notification.MockMailbox;
import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Authentication: wrong password, inactive user, expired token, registration rules, reset. */
class AuthenticationTest extends IntegrationTestSupport {

    @Autowired
    UserAccountRepository users;
    @Autowired
    AuditEventRepository auditEvents;
    @Autowired
    JwtEncoder jwtEncoder;
    @Autowired
    MockMailbox mailbox;

    @Test
    void publicRegistrationAlwaysAssignsCustomerEvenIfAnotherRoleIsRequested() throws Exception {
        String email = uniqueEmail("role-escalation");
        perform(json(post("/api/v1/auth/register"), """
                {"fullName":"Eve","email":"%s","mobile":"+91 90000 00000","password":"%s",
                 "acceptedTerms":true,"role":"ADMIN"}""".formatted(email, CUSTOMER_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void passwordIsStoredAsAdaptiveHashNeverPlaintext() throws Exception {
        Session c = registerCustomer();
        UserAccount stored = users.findByEmail(c.email()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").doesNotContain(CUSTOMER_PASSWORD);
    }

    @Test
    void duplicateEmailReturnsSafeConflictIgnoringCase() throws Exception {
        Session c = registerCustomer();
        perform(json(post("/api/v1/auth/register"), """
                {"fullName":"Dup","email":"%s","mobile":"+91 90000 00000","password":"%s","acceptedTerms":true}"""
                .formatted(c.email().toUpperCase(), CUSTOMER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
    }

    @Test
    void weakPasswordAndMissingTermsAreRejected() throws Exception {
        perform(json(post("/api/v1/auth/register"), """
                {"fullName":"Weak","email":"%s","mobile":"+91 90000 00000","password":"short","acceptedTerms":true}"""
                .formatted(uniqueEmail("weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        perform(json(post("/api/v1/auth/register"), """
                {"fullName":"NoTerms","email":"%s","mobile":"+91 90000 00000","password":"%s","acceptedTerms":false}"""
                .formatted(uniqueEmail("terms"), CUSTOMER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGeneric401AndNoToken() throws Exception {
        Session c = registerCustomer();
        MvcResult wrong = perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + c.email() + "\",\"password\":\"Wrong-Pass-9\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();
        MvcResult unknown = perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + uniqueEmail("nobody") + "\",\"password\":\"Wrong-Pass-9\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(read(wrong, "$.message")).isEqualTo(read(unknown, "$.message"));
        assertThat(auditEvents.findByAction("LOGIN_FAILED")).isNotEmpty();
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        Session c = registerCustomer();
        Session admin = staff("admin");
        perform(authed(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/users/" + c.userId()), "{\"status\":\"DISABLED\"}"), admin))
                .andExpect(status().isOk());
        perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + c.email() + "\",\"password\":\"" + CUSTOMER_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void repeatedFailuresAreThrottled() throws Exception {
        Session c = registerCustomer();
        for (int i = 0; i < 5; i++) {
            perform(json(post("/api/v1/auth/login"),
                    "{\"email\":\"" + c.email() + "\",\"password\":\"Wrong-Pass-9\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused while throttled.
        perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + c.email() + "\",\"password\":\"" + CUSTOMER_PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void validTokenAuthenticatesAndMissingTokenIs401() throws Exception {
        Session c = registerCustomer();
        perform(authed(get("/api/v1/customers/me"), c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(c.userId()));
        perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredTamperedOrWrongAudienceTokensAreRejected() throws Exception {
        Session c = registerCustomer();
        String expired = token(c.userId(), "plms-auth", "plms-api", Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(1800));
        String wrongAudience = token(c.userId(), "plms-auth", "another-api", Instant.now(),
                Instant.now().plusSeconds(600));
        String wrongIssuer = token(c.userId(), "evil-issuer", "plms-api", Instant.now(),
                Instant.now().plusSeconds(600));
        String tampered = c.accessToken().substring(0, c.accessToken().length() - 3) + "abc";
        for (String bad : List.of(expired, wrongAudience, wrongIssuer, tampered)) {
            perform(get("/api/v1/customers/me").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void passwordResetIsNeutralForUnknownEmailAndRotatesCredentials() throws Exception {
        perform(json(post("/api/v1/auth/password-reset/request"), "{\"email\":\"" + uniqueEmail("ghost") + "\"}"))
                .andExpect(status().isAccepted());

        Session c = registerCustomer();
        MvcResult known = perform(json(post("/api/v1/auth/password-reset/request"),
                "{\"email\":\"" + c.email() + "\"}")).andExpect(status().isAccepted()).andReturn();
        assertThat(read(known, "$.message")).contains("If the address is registered");

        String token = mailbox.lastResetToken(c.email()).orElseThrow();
        perform(json(post("/api/v1/auth/password-reset/confirm"),
                "{\"token\":\"" + token + "\",\"newPassword\":\"Brand-New-Pass-2\"}"))
                .andExpect(status().isOk());

        // Single use, old refresh sessions revoked, old password no longer works.
        perform(json(post("/api/v1/auth/password-reset/confirm"),
                "{\"token\":\"" + token + "\",\"newPassword\":\"Another-Pass-3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + c.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        perform(json(post("/api/v1/auth/login"),
                "{\"email\":\"" + c.email() + "\",\"password\":\"" + CUSTOMER_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        login(c.email(), "Brand-New-Pass-2");
    }

    private String token(String subject, String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject)
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", List.of("CUSTOMER")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
