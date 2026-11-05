package com.example.plms.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Externalised, validated-at-startup configuration. Secrets come from the environment. */
@ConfigurationProperties(prefix = "plms")
public record PlmsProperties(String timeZone, Security security, Documents documents, Approval approval,
                             Credit credit, Seed seed) {

    public record Security(String jwtSecret, String issuer, String audience, Duration accessTokenTtl,
                           Duration refreshTokenTtl, Duration resetTokenTtl, int maxFailedLogins,
                           Duration lockoutWindow) {
    }

    public record Documents(String storageDir, long maxBytes, List<String> mandatoryCategories) {
    }

    public record Approval(boolean makerChecker, boolean requireRecommendation) {
    }

    public record Credit(Duration timeout, int retryAttempts, int circuitWindowSize, float circuitFailureRate,
                         Duration circuitWaitOpen) {
    }

    public record Seed(boolean enabled, String staffPassword) {
    }
}
