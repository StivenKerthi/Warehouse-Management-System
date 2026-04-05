package com.example.wms.common.exception;

import com.example.wms.common.dto.ErrorResponse;
import com.example.wms.common.filter.CorrelationIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Central error handler — converts every exception into a consistent
 * {@link ErrorResponse} JSON body.
 *
 * <p>Hierarchy (most specific → least specific):
 * <ol>
 *   <li>{@link BusinessException}              → 422 Unprocessable Entity</li>
 *   <li>{@link StateMachineException}           → 409 Conflict</li>
 *   <li>{@link AccessDeniedException}           → 403 Forbidden</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request</li>
 *   <li>{@link EntityNotFoundException}         → 404 Not Found</li>
 *   <li>{@link Exception}                       → 500 Internal Server Error</li>
 * </ol>
 *
 * <p>The {@code correlationId} is read from MDC (populated by
 * {@link CorrelationIdFilter} earlier in the filter chain) so every error
 * response is traceable.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // 422 — Business rule violations
    // -------------------------------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 409 — Illegal order state transitions
    // -------------------------------------------------------------------------

    @ExceptionHandler(StateMachineException.class)
    public ResponseEntity<ErrorResponse> handleStateMachine(StateMachineException ex) {
        log.warn("State machine violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 403 — Authorisation failures (role mismatch, ownership check)
    // -------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        // Do not log at WARN — routine for bad actors; INFO is sufficient.
        log.info("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action.");
    }

    // -------------------------------------------------------------------------
    // 400 — Bean Validation failures (@Valid on request bodies / params)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .map(err -> {
                    if (err instanceof FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return err.getDefaultMessage();
                })
                .sorted()
                .collect(Collectors.joining("; "));

        log.debug("Validation failed: {}", detail);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    // -------------------------------------------------------------------------
    // 404 — Entity not found
    // -------------------------------------------------------------------------

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        log.debug("Entity not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // 500 — Unexpected errors (last resort)
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        ErrorResponse body = ErrorResponse.of(status.value(), code, message, correlationId());
        return ResponseEntity.status(status).body(body);
    }

    /** Reads the correlation ID that {@link CorrelationIdFilter} placed in MDC. */
    private static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_CORRELATION_KEY);
    }
}
