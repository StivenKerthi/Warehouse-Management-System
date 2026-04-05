package com.example.wms.common.exception;

/**
 * Thrown when an order state transition is illegal (HTTP 409 Conflict).
 *
 * <p>Examples: attempting to approve an order that is already FULFILLED,
 * or cancelling a CANCELED order.
 */
public class StateMachineException extends RuntimeException {

    private final String errorCode;

    public StateMachineException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
