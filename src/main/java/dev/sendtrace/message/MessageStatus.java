package dev.sendtrace.message;

public enum MessageStatus {
    // Written, and its outbox event exists - delivery workers own the transitions below.
    ACCEPTED,
    SENT,
    FAILED
}
