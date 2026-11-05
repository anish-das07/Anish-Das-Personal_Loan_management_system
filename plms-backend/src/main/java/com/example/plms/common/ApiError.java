package com.example.plms.common;

import java.time.Instant;
import java.util.List;

/** Standard error shape (case study section 7). Never carries stack traces or internal data. */
public record ApiError(Instant timestamp, int status, String code, String message, String correlationId,
                       List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, CorrelationIdFilter.currentId(),
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors));
    }

    /** Minimal hand-written JSON for filters that run outside Spring MVC message conversion. */
    public String toJson() {
        return "{\"timestamp\":\"" + timestamp + "\",\"status\":" + status + ",\"code\":\"" + esc(code)
                + "\",\"message\":\"" + esc(message) + "\",\"correlationId\":"
                + (correlationId == null ? "null" : "\"" + esc(correlationId) + "\"")
                + ",\"fieldErrors\":[]}";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
