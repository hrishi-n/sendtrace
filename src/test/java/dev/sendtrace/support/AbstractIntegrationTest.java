package dev.sendtrace.support;

import dev.sendtrace.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Boots the Spring context against one shared throwaway Postgres container.
// SqsClient is mocked - the outbox publisher's actual delivery to SQS/LocalStack is exercised
// separately, not on every test that just needs a message and its outbox row written.
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // tests call OutboxPublisher.publishPending() explicitly; keep the background
        // scheduler from also firing mid-test and racing the assertions.
        registry.add("sendtrace.outbox.poll-interval-ms", () -> "600000");
    }

    @MockBean
    protected SqsClient sqsClient;

    protected UUID tenant;

    @BeforeEach
    void bindTenant() {
        tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenReturn(SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }
}
