package dev.sendtrace.error;

import org.springframework.http.HttpStatus;

// Base type for domain errors, each mapping to an HTTP status and an error code.
public abstract class SendTraceException extends RuntimeException {

    protected SendTraceException(String message) {
        super(message);
    }

    public abstract HttpStatus status();

    public abstract String code();
}
