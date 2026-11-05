package com.example.plms.common;

import java.util.List;

/** Domain/application exception carrying an HTTP status and a stable machine-readable code. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient List<ApiError.FieldError> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(int status, String code, String message, List<ApiError.FieldError> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ApiError.FieldError> details() {
        return details;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(403, code, message);
    }

    /** Used both for missing resources and for resources intentionally concealed from the caller. */
    public static ApiException notFound(String what) {
        return new ApiException(404, "NOT_FOUND", what + " was not found.");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

    public static ApiException invalidState() {
        return new ApiException(409, "INVALID_STATE_TRANSITION",
                "The requested action is not valid for the current state.");
    }

    public static ApiException tooLarge(String message) {
        return new ApiException(413, "PAYLOAD_TOO_LARGE", message);
    }

    public static ApiException unsupportedMedia(String message) {
        return new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, "TOO_MANY_REQUESTS", message);
    }
}
