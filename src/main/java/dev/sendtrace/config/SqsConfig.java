package dev.sendtrace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(@Value("${aws.region}") String region,
                                @Value("${aws.sqs-endpoint:}") String endpointOverride) {
        var builder = SqsClient.builder().region(Region.of(region));
        if (!endpointOverride.isBlank()) {
            // LocalStack has no real IAM, so any non-empty static credentials satisfy the SDK.
            builder.endpointOverride(URI.create(endpointOverride))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        return builder.build();
    }
}
