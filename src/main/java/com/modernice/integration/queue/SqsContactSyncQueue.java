package com.modernice.integration.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("!local")
public class SqsContactSyncQueue implements ContactSyncQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsContactSyncQueue.class);
    private static final int MAX_BATCH_SIZE = 10;

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final String deadLetterQueueUrl;

    public SqsContactSyncQueue(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${integration.sqs.queue-url}") String queueUrl,
            @Value("${integration.sqs.dead-letter-queue-url}") String deadLetterQueueUrl) {
        Assert.hasText(queueUrl, "integration.sqs.queue-url must be configured outside the local profile");
        Assert.hasText(deadLetterQueueUrl, "integration.sqs.dead-letter-queue-url must be configured outside the local profile");
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.deadLetterQueueUrl = deadLetterQueueUrl;
    }

    @Override
    public Set<UUID> enqueue(List<ContactSyncMessage> messages) {
        Set<UUID> enqueued = new HashSet<>();
        for (int start = 0; start < messages.size(); start += MAX_BATCH_SIZE) {
            List<ContactSyncMessage> chunk = messages.subList(start, Math.min(start + MAX_BATCH_SIZE, messages.size()));
            List<SendMessageBatchRequestEntry> entries = chunk.stream()
                    .map(message -> SendMessageBatchRequestEntry.builder()
                            .id(message.itemId().toString())
                            .messageBody(serialize(message))
                            .build())
                    .toList();

            try {
                SendMessageBatchResponse response = sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
                        .queueUrl(queueUrl)
                        .entries(entries)
                        .build());

                response.successful().forEach(result -> enqueued.add(UUID.fromString(result.id())));
                response.failed().forEach(failure -> LOGGER.error(
                        "Failed to enqueue contact sync item {}: {}",
                        failure.id(),
                        failure.message()
                ));
            } catch (SdkException exception) {
                LOGGER.error("Failed to enqueue a contact sync message batch", exception);
            }
        }
        return enqueued;
    }

    @Override
    public List<ContactSyncDelivery> receive(int maxMessages) {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(Math.min(maxMessages, MAX_BATCH_SIZE))
                        .waitTimeSeconds(20)
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                        .build())
                .messages();

        List<ContactSyncDelivery> deliveries = new ArrayList<>(messages.size());
        for (Message message : messages) {
            int receiveCount = Integer.parseInt(
                    message.attributesAsStrings().getOrDefault("ApproximateReceiveCount", "1")
            );
            deliveries.add(new ContactSyncDelivery(
                    deserialize(message.body()),
                    message.receiptHandle(),
                    receiveCount
            ));
        }
        return deliveries;
    }

    @Override
    public void acknowledge(ContactSyncDelivery delivery) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(delivery.receiptHandle())
                .build());
    }

    @Override
    public void retry(ContactSyncDelivery delivery) {
        // Leaving a failed delivery undeleted lets SQS redeliver it after the visibility timeout.
    }

    @Override
    public void deadLetter(ContactSyncDelivery delivery, String reason) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(deadLetterQueueUrl)
                .messageBody(serialize(delivery.message()))
                .messageAttributes(java.util.Map.of(
                        "failureReason",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(reason)
                                .build()
                ))
                .build());
        acknowledge(delivery);
    }

    private String serialize(ContactSyncMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize contact sync message", exception);
        }
    }

    private ContactSyncMessage deserialize(String body) {
        try {
            return objectMapper.readValue(body, ContactSyncMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize contact sync message", exception);
        }
    }
}
