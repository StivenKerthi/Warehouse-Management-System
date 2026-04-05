package com.example.wms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a business rule is violated (HTTP 422 Unprocessable Entity).
 *
 * <p>Examples: insufficient truck volume, scheduling on a weekend,
 * updating an order whose status does not allow modifications.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public BusinessException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status    = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
