package com.modernice.integration;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.AccountSyncBatchEntity;
import com.modernice.integration.persistence.entity.AccountSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.AccountSyncBatchItemRepository;
import com.modernice.integration.persistence.repository.AccountSyncBatchRepository;
import com.modernice.integration.queue.AccountSyncDelivery;
import com.modernice.integration.queue.AccountSyncMessage;
import com.modernice.integration.queue.AccountSyncQueue;
import com.modernice.integration.workflow.AccountNotFoundException;
import com.modernice.integration.workflow.AccountSyncBatchService;
import com.modernice.integration.workflow.AccountSyncQueueWorker;
import com.modernice.integration.workflow.AccountSyncWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSyncBatchTests {

    @Mock
    private AccountSyncQueue accountSyncQueue;
    @Mock
    private AccountSyncBatchRepository batchRepository;
    @Mock
    private AccountSyncBatchItemRepository itemRepository;
    @Mock
    private AccountSyncWorkflow accountSyncWorkflow;

    @Test
    void submitCreatesQueueMessageForEachAccount() {
        when(accountSyncQueue.enqueue(anyList())).thenAnswer(invocation ->
                ((List<AccountSyncMessage>) invocation.getArgument(0)).stream()
                        .map(AccountSyncMessage::itemId)
                        .collect(Collectors.toSet()));
        when(batchRepository.findById(any())).thenAnswer(invocation -> Optional.of(AccountSyncBatchEntity.builder()
                .id(invocation.getArgument(0))
                .totalCount(2)
                .createdAt(Instant.now())
                .build()));
        when(itemRepository.countByBatchIdAndStatus(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) == ContactSyncItemStatus.QUEUED ? 2L : 0L);

        var service = new AccountSyncBatchService(accountSyncQueue, batchRepository, itemRepository);
        var response = service.submit(List.of("A500", "A501"));

        assertThat(response.totalAccounts()).isEqualTo(2);
        assertThat(response.queued()).isEqualTo(2);
        ArgumentCaptor<List<AccountSyncMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(accountSyncQueue).enqueue(messages.capture());
        assertThat(messages.getValue()).extracting(AccountSyncMessage::accountId)
                .containsExactly("A500", "A501");
    }

    @Test
    void workerMarksKnownAccountSucceeded() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        AccountSyncBatchItemEntity item = AccountSyncBatchItemEntity.builder()
                .id(itemId)
                .batchId(batchId)
                .accountId("A500")
                .status(ContactSyncItemStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        AccountSyncDelivery delivery = new AccountSyncDelivery(
                new AccountSyncMessage(batchId, itemId, "A500"),
                "receipt",
                1
        );
        when(accountSyncQueue.receive(10)).thenReturn(List.of(delivery));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountSyncWorkflow.syncInforAccountToHubSpot("A500")).thenReturn(Mono.empty());

        var worker = new AccountSyncQueueWorker(accountSyncQueue, itemRepository, accountSyncWorkflow);
        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        worker.processMessages();

        assertThat(item.getStatus()).isEqualTo(ContactSyncItemStatus.SUCCEEDED);
        verify(accountSyncQueue).acknowledge(delivery);
    }

    @Test
    void workerDeadLettersMissingAccount() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        AccountSyncBatchItemEntity item = AccountSyncBatchItemEntity.builder()
                .id(itemId)
                .batchId(batchId)
                .accountId("missing")
                .status(ContactSyncItemStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        AccountSyncDelivery delivery = new AccountSyncDelivery(
                new AccountSyncMessage(batchId, itemId, "missing"),
                "receipt",
                1
        );
        when(accountSyncQueue.receive(10)).thenReturn(List.of(delivery));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountSyncWorkflow.syncInforAccountToHubSpot("missing"))
                .thenReturn(Mono.error(new AccountNotFoundException("missing")));

        var worker = new AccountSyncQueueWorker(accountSyncQueue, itemRepository, accountSyncWorkflow);
        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        worker.processMessages();

        assertThat(item.getStatus()).isEqualTo(ContactSyncItemStatus.FAILED);
        verify(accountSyncQueue).deadLetter(delivery, "INFOR CRM account not found: missing");
    }
}
