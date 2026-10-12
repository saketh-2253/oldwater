package com.modernice.integration.workflow;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.AccountSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.AccountSyncBatchItemRepository;
import com.modernice.integration.queue.AccountSyncDelivery;
import com.modernice.integration.queue.AccountSyncQueue;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AccountSyncQueueWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSyncQueueWorker.class);
    private static final int RECEIVE_BATCH_SIZE = 10;

    private final AccountSyncQueue accountSyncQueue;
    private final AccountSyncBatchItemRepository itemRepository;
    private final AccountSyncWorkflow accountSyncWorkflow;

    @Value("${integration.sqs.max-attempts:5}")
    private int maxAttempts;

    @PostConstruct
    void validateConfiguration() {
        Assert.isTrue(maxAttempts > 0, "integration.sqs.max-attempts must be greater than zero");
    }

    @Scheduled(fixedDelayString = "${integration.sqs.poll-interval-ms:1000}")
    public void processMessages() {
        List<AccountSyncDelivery> deliveries = accountSyncQueue.receive(RECEIVE_BATCH_SIZE);
        Flux.fromIterable(deliveries)
                .flatMap(delivery -> Mono.fromRunnable(() -> processMessage(delivery))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(exception -> {
                            LOGGER.error("Unable to process account sync queue message {}",
                                    delivery.message().itemId(), exception);
                            return Mono.empty();
                        }), RECEIVE_BATCH_SIZE)
                .then()
                .block();
    }

    private void processMessage(AccountSyncDelivery delivery) {
        AccountSyncBatchItemEntity item = itemRepository.findById(delivery.message().itemId()).orElse(null);
        if (item == null) {
            LOGGER.error("Discarding account sync item {} because its batch record is missing",
                    delivery.message().itemId());
            accountSyncQueue.acknowledge(delivery);
            return;
        }
        if (item.getStatus() == ContactSyncItemStatus.SUCCEEDED) {
            accountSyncQueue.acknowledge(delivery);
            return;
        }
        if (item.getStatus() == ContactSyncItemStatus.FAILED) {
            accountSyncQueue.deadLetter(delivery, item.getLastError());
            return;
        }

        item.setStatus(ContactSyncItemStatus.PROCESSING);
        item.setUpdatedAt(Instant.now());
        item = itemRepository.save(item);
        try {
            accountSyncWorkflow.syncInforAccountToHubSpot(item.getAccountId()).block();
        } catch (RuntimeException exception) {
            String reason = conciseMessage(exception);
            LOGGER.error("Account sync failed for INFOR account {} (attempt {} of {})",
                    item.getAccountId(), delivery.receiveCount(), maxAttempts, exception);
            item.setLastError(reason);
            item.setUpdatedAt(Instant.now());
            if (exception instanceof AccountNotFoundException || delivery.receiveCount() >= maxAttempts) {
                item.setStatus(ContactSyncItemStatus.FAILED);
                itemRepository.save(item);
                accountSyncQueue.deadLetter(delivery, reason);
            } else {
                item.setStatus(ContactSyncItemStatus.QUEUED);
                itemRepository.save(item);
                accountSyncQueue.retry(delivery);
            }
            return;
        }
        item.setStatus(ContactSyncItemStatus.SUCCEEDED);
        item.setLastError(null);
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
        accountSyncQueue.acknowledge(delivery);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
