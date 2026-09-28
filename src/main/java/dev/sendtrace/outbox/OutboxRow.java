package dev.sendtrace.outbox;

import java.util.UUID;

public record OutboxRow(UUID id, UUID tenantId, String eventType, String payload) {}
