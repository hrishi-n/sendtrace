package dev.sendtrace.error;

import org.springframework.http.HttpStatus;

// Concrete domain errors, grouped for brevity.
public final class SendTraceExceptions {

    private SendTraceExceptions() {}

    public static final class NotFound extends SendTraceException {
        public NotFound(String message) {
            super(message);
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.NOT_FOUND;
        }

        @Override
        public String code() {
            return "not_found";
        }
    }

    public static final class IdempotencyConflict extends SendTraceException {
        public IdempotencyConflict(String key) {
            super("idempotency key '%s' was already used with a different request body".formatted(key));
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "idempotency_conflict";
        }
    }
}
