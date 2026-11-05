package com.example.plms;

import com.example.plms.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 14 - Refresh revocation: rotation, replay detection, logout. */
class RefreshTokenTest extends IntegrationTestSupport {

    private MvcResult refresh(String token) throws Exception {
        return perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + token + "\"}")).andReturn();
    }

    @Test
    void refreshRotatesTheTokenPair() throws Exception {
        Session c = registerCustomer();
        MvcResult r = refresh(c.refreshToken());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(r, "$.refreshToken")).isNotEqualTo(c.refreshToken());
        assertThat(read(r, "$.accessToken")).isNotBlank();
    }

    @Test
    void reusingARotatedTokenIsRejectedAndRevokesTheWholeFamily() throws Exception {
        Session c = registerCustomer();
        String rotated = read(refresh(c.refreshToken()), "$.refreshToken");

        // Replay of the old token: rejected, and treated as theft.
        assertThat(refresh(c.refreshToken()).getResponse().getStatus()).isEqualTo(401);
        // The legitimately rotated token from the same family is now revoked too.
        assertThat(refresh(rotated).getResponse().getStatus()).isEqualTo(401);
        // A fresh login still works (new family).
        login(c.email(), CUSTOMER_PASSWORD);
    }

    @Test
    void logoutRevokesTheRefreshSession() throws Exception {
        Session c = registerCustomer();
        perform(authed(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"" + c.refreshToken() + "\"}"), c))
                .andExpect(status().isNoContent());
        assertThat(refresh(c.refreshToken()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        assertThat(refresh("not-a-real-token").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anotherUsersLogoutCannotRevokeMySession() throws Exception {
        Session me = registerCustomer();
        Session other = registerCustomer();
        perform(authed(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"" + me.refreshToken() + "\"}"), other))
                .andExpect(status().isNoContent());
        assertThat(refresh(me.refreshToken()).getResponse().getStatus()).isEqualTo(200);
    }
}
