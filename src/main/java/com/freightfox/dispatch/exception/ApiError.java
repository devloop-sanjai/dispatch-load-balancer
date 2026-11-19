package com.freightfox.dispatch.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(String status, String message, List<String> errors, Instant timestamp) {

    public static ApiError of(String message, List<String> errors) {
        return new ApiError("error", message, errors, Instant.now());
    }
}
