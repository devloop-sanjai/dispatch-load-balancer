package com.freightfox.dispatch.exception;

/** A plan was requested but the required data has not been submitted yet. Mapped to HTTP 422. */
public class DispatchNotReadyException extends RuntimeException {

    public DispatchNotReadyException(String message) {
        super(message);
    }
}
