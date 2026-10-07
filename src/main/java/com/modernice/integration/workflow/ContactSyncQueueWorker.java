package com.modernice.integration.workflow;

import jakarta.annotation.PostConstruct;
import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.ContactSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.ContactSyncBatchItemRepository;
import com.modernice.integration.queue.ContactSyncDelivery;
import com.modernice.integration.queue.ContactSyncQueue;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class ContactSyncQueueWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactSyncQueueWorker.class);
    private static final int RECEIVE_BATCH_SIZE = 10;

    private final ContactSyncQueue contactSyncQueue;
    private final ContactSyncBatchItemRepository itemRepository;
    private final ContactSyncWorkflow contactSyncWorkflow;

    @Value("${integration.sqs.max-attempts:5}")
    private int maxAttempts;

    @PostConstruct
    void validateConfiguration() {
        Assert.isTrue(maxAttempts > 0, "integration.sqs.max-attempts must be greater than zero");
    }

    @Scheduled(fixedDelayString = "${integration.sqs.poll-interval-ms:1000}")
    public void processMessages() {
        List<ContactSyncDelivery> deliveries = contactSyncQueue.receive(RECEIVE_BATCH_SIZE);
        Flux.fromIterable(deliveries)
                .flatMap(delivery -> Mono.fromRunnable(() -> processMessage(delivery))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(exception -> {
                            LOGGER.error("Unable to process contact sync queue message {}", delivery.message().itemId(), exception);
                            return Mono.empty();
                        }), RECEIVE_BATCH_SIZE)
                .then()
                .block();
    }

    private void processMessage(ContactSyncDelivery delivery) {
        ContactSyncBatchItemEntity item = itemRepository.findById(delivery.message().itemId()).orElse(null);
        if (item == null) {
            LOGGER.error("Discarding queued contact sync item {} because its batch record is missing",
                    delivery.message().itemId());
            contactSyncQueue.acknowledge(delivery);
            return;
        }
        if (item.getStatus() == ContactSyncItemStatus.SUCCEEDED) {
            contactSyncQueue.acknowledge(delivery);
            return;
        }
        if (item.getStatus() == ContactSyncItemStatus.FAILED) {
            contactSyncQueue.deadLetter(delivery, item.getLastError());
            return;
        }

        item.setStatus(ContactSyncItemStatus.PROCESSING);
        item.setUpdatedAt(Instant.now());
        item = itemRepository.save(item);
        try {
            contactSyncWorkflow.syncInforContactToHubSpot(item.getCrmContactId()).block();
        } catch (RuntimeException exception) {
            String reason = conciseMessage(exception);
            LOGGER.error("Contact sync failed for CRM contact {} (attempt {} of {})",
                    item.getCrmContactId(), delivery.receiveCount(), maxAttempts, exception);
            item.setLastError(reason);
            item.setUpdatedAt(Instant.now());
            if (exception instanceof ContactNotFoundException || delivery.receiveCount() >= maxAttempts) {
                item.setStatus(ContactSyncItemStatus.FAILED);
                itemRepository.save(item);
                contactSyncQueue.deadLetter(delivery, reason);
            } else {
                item.setStatus(ContactSyncItemStatus.QUEUED);
                itemRepository.save(item);
                contactSyncQueue.retry(delivery);
            }
            return;
        }

        item.setStatus(ContactSyncItemStatus.SUCCEEDED);
        item.setLastError(null);
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        contactSyncQueue.acknowledge(delivery);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
