package dev.sendtrace.message;

import dev.sendtrace.error.SendTraceExceptions;
import dev.sendtrace.outbox.OutboxPublisher;
import dev.sendtrace.outbox.OutboxRepository;
import dev.sendtrace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class SubmitMessageIntegrationTest extends AbstractIntegrationTest {

    @Autowired MessageService messages;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxPublisher publisher;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void submitting_a_message_makes_it_readable_and_queues_an_outbox_event() {
        MessageView submitted = messages.submit(Channel.SMS, "+15551234567", "hello", null, null);

        assertThat(submitted.status()).isEqualTo(MessageStatus.ACCEPTED);

        MessageView fetched = messages.get(submitted.id());
        assertThat(fetched.recipient()).isEqualTo("+15551234567");

        publisher.publishPending();

        // The poller is global and cross-tenant by design, so it may also drain rows left
        // behind by other tests in this class - assert our row was among what got published,
        // not that it was the only thing published.
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(req -> req.messageBody().contains(submitted.id().toString()));

        // A second poll has nothing left to claim, since every pending row was just published.
        transactionTemplate.executeWithoutResult(status -> assertThat(outbox.claimBatch(10)).isEmpty());
    }

    @Test
    void same_idempotency_key_returns_the_same_message() {
        var req = new MessageController.SubmitMessageRequest(Channel.EMAIL, "a@example.com", "hi");

        MessageView first = messages.submit(req.channel(), req.recipient(), req.body(), "key-1", req);
        MessageView second = messages.submit(req.channel(), req.recipient(), req.body(), "key-1", req);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void reusing_a_key_with_a_different_body_is_rejected() {
        messages.submit(Channel.EMAIL, "a@example.com", "hi", "key-2", "hi");

        assertThatThrownBy(() ->
                messages.submit(Channel.EMAIL, "a@example.com", "different body", "key-2", "different body"))
            .isInstanceOf(SendTraceExceptions.IdempotencyConflict.class);
    }
}
