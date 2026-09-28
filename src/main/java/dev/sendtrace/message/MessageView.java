package dev.sendtrace.message;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
    UUID id,
    Channel channel,
    String recipient,
    MessageStatus status,
    Instant createdAt
) {}
