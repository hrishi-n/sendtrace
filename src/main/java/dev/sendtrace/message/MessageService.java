package dev.sendtrace.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sendtrace.error.SendTraceExceptions;
import dev.sendtrace.idempotency.IdempotencyRepository;
import dev.sendtrace.idempotency.RequestHasher;
import dev.sendtrace.outbox.OutboxRepository;
import dev.sendtrace.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// Writes a message and its outbox event in one transaction, so a caller that saw
// the message accepted is guaranteed the delivery event will eventually be published.
@Service
public class MessageService {

    private final MessageRepository messages;
    private final OutboxRepository outbox;
    private final IdempotencyRepository idempotency;
    private final RequestHasher hasher;
    private final ObjectMapper objectMapper;

    public MessageService(MessageRepository messages, OutboxRepository outbox,
                           IdempotencyRepository idempotency, RequestHasher hasher, ObjectMapper objectMapper) {
        this.messages = messages;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageView submit(Channel channel, String recipient, String body,
                               String idempotencyKey, Object idempotencyPayload) {
        UUID tenantId = TenantContext.require();
        String fingerprint = idempotencyKey == null ? null : hasher.fingerprint(idempotencyPayload);

        if (idempotencyKey != null) {
            MessageView replay = replay(tenantId, idempotencyKey, fingerprint);
            if (replay != null) {
                return replay;
            }
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        messages.insert(id, tenantId, channel, recipient, body, MessageStatus.ACCEPTED, now);
        outbox.insert(id, tenantId, "message", id, "message.submitted",
            serialize(new OutboxMessagePayload(id, tenantId, channel, recipient, body, now)), now);

        MessageView view = new MessageView(id, channel, recipient, MessageStatus.ACCEPTED, now);

        if (idempotencyKey != null) {
            try {
                idempotency.save(tenantId, idempotencyKey, fingerprint, 201, serialize(view), id);
            } catch (DuplicateKeyException raced) {
                // Falls back to the stored result of the duplicate that won the insert.
                MessageView replayed = replay(tenantId, idempotencyKey, fingerprint);
                if (replayed != null) {
                    return replayed;
                }
                throw raced;
            }
        }
        return view;
    }

    @Transactional(readOnly = true)
    public MessageView get(UUID id) {
        return messages.find(TenantContext.require(), id)
            .orElseThrow(() -> new SendTraceExceptions.NotFound("message " + id + " not found"));
    }

    private MessageView replay(UUID tenantId, String idempotencyKey, String fingerprint) {
        return idempotency.find(tenantId, idempotencyKey)
            .map(stored -> {
                if (!stored.requestHash().equals(fingerprint)) {
                    throw new SendTraceExceptions.IdempotencyConflict(idempotencyKey);
                }
                return deserialize(stored.responseBody());
            })
            .orElse(null);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MessageView deserialize(String json) {
        try {
            return objectMapper.readValue(json, MessageView.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record OutboxMessagePayload(
        UUID messageId, UUID tenantId, Channel channel, String recipient, String body, Instant createdAt
    ) {}
}
