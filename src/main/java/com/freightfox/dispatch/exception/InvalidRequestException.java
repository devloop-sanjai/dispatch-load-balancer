package com.freightfox.dispatch.exception;

/** Request is well-formed but violates a business rule (e.g. duplicate ids). Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
