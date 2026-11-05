package com.example.plms.notification;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for an email provider for messages that carry secrets (password-reset links).
 * The secret is held only in memory for the demo/test "inbox"; it is never logged or persisted.
 * Replace with a real provider adapter in a production deployment.
 */
@Component
public class MockMailbox {

    private final Map<String, String> lastResetTokenByEmail = new ConcurrentHashMap<>();

    public void deliverPasswordReset(String email, String rawToken) {
        lastResetTokenByEmail.put(email, rawToken);
    }

    public Optional<String> lastResetToken(String email) {
        return Optional.ofNullable(lastResetTokenByEmail.get(email));
    }
}
