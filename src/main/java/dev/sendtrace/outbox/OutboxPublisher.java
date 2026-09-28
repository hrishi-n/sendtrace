package dev.sendtrace.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Drains unpublished outbox rows into SQS. Runs inside one transaction per batch, so a row
// stays locked (FOR UPDATE SKIP LOCKED) until it is either published or its attempt count bumps -
// simple and correct at this scale, though a busier queue would want to publish outside the lock.
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final SqsClient sqs;
    private final String queueUrl;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outbox, SqsClient sqs,
                            @Value("${sendtrace.outbox.queue-url}") String queueUrl,
                            @Value("${sendtrace.outbox.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${sendtrace.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxRow> batch = outbox.claimBatch(batchSize);
        for (OutboxRow row : batch) {
            try {
                sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(row.payload())
                    .messageAttributes(Map.of(
                        "eventType", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(row.eventType())
                            .build(),
                        "tenantId", MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(row.tenantId().toString())
                            .build()))
                    .build());
                outbox.markPublished(row.id(), Instant.now());
            } catch (Exception e) {
                log.warn("failed to publish outbox event {}: {}", row.id(), e.getMessage());
                outbox.incrementAttempts(row.id());
            }
        }
    }
}
