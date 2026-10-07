package com.modernice.integration.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@Profile("!local")
public class SqsConfiguration {

    @Bean
    SqsClient sqsClient(@Value("${integration.sqs.region:us-east-1}") String region) {
        return SqsClient.builder()
                .region(Region.of(region))
                .build();
    }
}
