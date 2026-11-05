package com.example.plms.common;

import com.example.plms.core.workflow.IllegalStateTransitionException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Maps exceptions to the standard {@link ApiError} shape with the status codes listed in section 7.
 * Messages are safe and generic; stack traces and provider responses are never returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) {
        return respond(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return respond(400, "VALIDATION_FAILED", "One or more fields are invalid.", fields);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidParameters(Exception ex) {
        return respond(400, "VALIDATION_FAILED", "One or more fields are invalid.", List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) {
        return respond(400, "MALFORMED_REQUEST", "The request body or parameters could not be read.", List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException ex) {
        return respond(400, "MISSING_HEADER", "Required header '" + ex.getHeaderName() + "' is missing.",
                List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ApiError> missingPart(Exception ex) {
        return respond(400, "VALIDATION_FAILED", "A required request parameter or part is missing.", List.of());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ResponseEntity<ApiError> invalidState(IllegalStateTransitionException ex) {
        return respond(409, "INVALID_STATE_TRANSITION",
                "The requested action is not valid for the current state.", List.of());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> concurrent(ConcurrencyFailureException ex) {
        return respond(409, "CONCURRENT_UPDATE",
                "The record was changed by another request. Reload and try again.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex) {
        return respond(409, "CONFLICT", "The request conflicts with existing data.", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
        return respond(413, "PAYLOAD_TOO_LARGE", "The uploaded file exceeds the maximum size.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(415, "UNSUPPORTED_MEDIA_TYPE", "The content type is not supported.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex) {
        return respond(405, "METHOD_NOT_ALLOWED", "The HTTP method is not supported here.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException ex) {
        return respond(404, "NOT_FOUND", "The requested resource was not found.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException ex) {
        return respond(403, "FORBIDDEN", "You do not have permission to perform this action.", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationException ex) {
        return respond(401, "UNAUTHORIZED", "Authentication is required.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unexpected error [{}]", ex.getClass().getSimpleName(), ex);
        return respond(500, "INTERNAL_ERROR", "An unexpected error occurred.", List.of());
    }

    private static ResponseEntity<ApiError> respond(int status, String code, String message,
                                                    List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, fields));
    }
}
