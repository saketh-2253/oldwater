package com.modernice.integration;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.ContactSyncBatchEntity;
import com.modernice.integration.persistence.entity.ContactSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.ContactSyncBatchItemRepository;
import com.modernice.integration.persistence.repository.ContactSyncBatchRepository;
import com.modernice.integration.queue.ContactSyncDelivery;
import com.modernice.integration.queue.ContactSyncMessage;
import com.modernice.integration.queue.ContactSyncQueue;
import com.modernice.integration.queue.LocalContactSyncQueue;
import com.modernice.integration.workflow.ContactNotFoundException;
import com.modernice.integration.workflow.ContactSyncBatchService;
import com.modernice.integration.workflow.ContactSyncQueueWorker;
import com.modernice.integration.workflow.ContactSyncWorkflow;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactSyncBatchTests {

    @Mock
    private ContactSyncQueue contactSyncQueue;

    @Mock
    private ContactSyncBatchRepository batchRepository;

    @Mock
    private ContactSyncBatchItemRepository itemRepository;

    @Mock
    private ContactSyncWorkflow contactSyncWorkflow;

    @Test
    void submitQueuesOneMessagePerContactAndReturnsBatchProgress() {
        when(contactSyncQueue.enqueue(anyList())).thenAnswer(invocation ->
                ((List<ContactSyncMessage>) invocation.getArgument(0)).stream()
                        .map(ContactSyncMessage::itemId)
                        .collect(java.util.stream.Collectors.toSet()));
        when(batchRepository.findById(any())).thenAnswer(invocation -> Optional.of(ContactSyncBatchEntity.builder()
                .id(invocation.getArgument(0))
                .totalCount(2)
                .createdAt(Instant.now())
                .build()));
        when(itemRepository.countByBatchIdAndStatus(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) == ContactSyncItemStatus.QUEUED ? 2L : 0L);

        var service = new ContactSyncBatchService(contactSyncQueue, batchRepository, itemRepository);
        var response = service.submit(List.of("103", "104"));

        assertThat(response.totalContacts()).isEqualTo(2);
        assertThat(response.queued()).isEqualTo(2);
        ArgumentCaptor<List<ContactSyncMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(contactSyncQueue).enqueue(messages.capture());
        assertThat(messages.getValue())
                .extracting(ContactSyncMessage::crmContactId)
                .containsExactly("103", "104");
    }

    @Test
    void localQueueReceivesMessagesInSqsSizedBatchesAndRetriesUnacknowledgedItems() {
        var queue = new LocalContactSyncQueue();
        List<ContactSyncMessage> messages = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new ContactSyncMessage(UUID.randomUUID(), UUID.randomUUID(), "crm-" + index))
                .toList();

        queue.enqueue(messages);
        List<ContactSyncDelivery> firstDelivery = queue.receive(10);
        assertThat(firstDelivery).hasSize(10);

        queue.acknowledge(firstDelivery.get(0));
        queue.retry(firstDelivery.get(1));
        List<ContactSyncDelivery> secondDelivery = queue.receive(10);

        assertThat(secondDelivery).hasSize(3);
        assertThat(secondDelivery)
                .extracting(delivery -> delivery.message().itemId())
                .contains(firstDelivery.get(1).message().itemId());
        assertThat(secondDelivery.stream()
                .filter(delivery -> delivery.message().itemId().equals(firstDelivery.get(1).message().itemId()))
                .findFirst()
                .orElseThrow()
                .receiveCount()).isEqualTo(2);
    }

    @Test
    void workerDeadLettersAnUnknownContactWithoutRetryingIt() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ContactSyncBatchItemEntity item = ContactSyncBatchItemEntity.builder()
                .id(itemId)
                .batchId(batchId)
                .crmContactId("missing")
                .status(ContactSyncItemStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        ContactSyncDelivery delivery = new ContactSyncDelivery(
                new ContactSyncMessage(batchId, itemId, "missing"),
                "receipt",
                1
        );
        when(contactSyncQueue.receive(10)).thenReturn(List.of(delivery));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(contactSyncWorkflow.syncInforContactToHubSpot("missing"))
                .thenReturn(Mono.error(new ContactNotFoundException("missing")));

        var worker = new ContactSyncQueueWorker(contactSyncQueue, itemRepository, contactSyncWorkflow);
        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        worker.processMessages();

        assertThat(item.getStatus()).isEqualTo(ContactSyncItemStatus.FAILED);
        verify(contactSyncQueue).deadLetter(delivery, "INFOR CRM contact not found: missing");
    }

    @Test
    void workerUsesSavedVersionWhenMarkingSuccessfulItem() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ContactSyncBatchItemEntity item = ContactSyncBatchItemEntity.builder()
                .id(itemId)
                .batchId(batchId)
                .crmContactId("103")
                .status(ContactSyncItemStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0)
                .build();
        ContactSyncBatchItemEntity persistedProcessingItem = ContactSyncBatchItemEntity.builder()
                .id(itemId)
                .batchId(batchId)
                .crmContactId("103")
                .status(ContactSyncItemStatus.PROCESSING)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .version(1)
                .build();
        ContactSyncDelivery delivery = new ContactSyncDelivery(
                new ContactSyncMessage(batchId, itemId, "103"),
                "receipt",
                1
        );
        when(contactSyncQueue.receive(10)).thenReturn(List.of(delivery));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenReturn(persistedProcessingItem, persistedProcessingItem);
        when(contactSyncWorkflow.syncInforContactToHubSpot("103")).thenReturn(Mono.empty());

        var worker = new ContactSyncQueueWorker(contactSyncQueue, itemRepository, contactSyncWorkflow);
        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        worker.processMessages();

        ArgumentCaptor<ContactSyncBatchItemEntity> saves =
                ArgumentCaptor.forClass(ContactSyncBatchItemEntity.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).save(saves.capture());
        assertThat(saves.getAllValues().get(0).getVersion()).isZero();
        assertThat(saves.getAllValues().get(1).getVersion()).isEqualTo(1);
        assertThat(saves.getAllValues().get(1).getStatus()).isEqualTo(ContactSyncItemStatus.SUCCEEDED);
        verify(contactSyncQueue).acknowledge(delivery);
    }
}
