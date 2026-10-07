package com.modernice.integration.workflow;

import com.modernice.integration.controller.dto.ContactSyncBatchStatusResponse;
import com.modernice.integration.controller.dto.ContactSyncBatchItemResponse;
import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.ContactSyncBatchEntity;
import com.modernice.integration.persistence.entity.ContactSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.ContactSyncBatchItemRepository;
import com.modernice.integration.persistence.repository.ContactSyncBatchRepository;
import com.modernice.integration.queue.ContactSyncMessage;
import com.modernice.integration.queue.ContactSyncQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactSyncBatchService {

    private final ContactSyncQueue contactSyncQueue;
    private final ContactSyncBatchRepository batchRepository;
    private final ContactSyncBatchItemRepository itemRepository;

    public ContactSyncBatchStatusResponse submit(List<String> contactIds) {
        if (contactIds == null || contactIds.isEmpty() || contactIds.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide between 1 and 10000 contact IDs");
        }
        if (contactIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact IDs must be non-blank and at most 255 characters");
        }
        if (new HashSet<>(contactIds).size() != contactIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact IDs must not contain duplicates");
        }

        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        batchRepository.save(ContactSyncBatchEntity.builder()
                .id(batchId)
                .totalCount(contactIds.size())
                .createdAt(now)
                .build());

        List<ContactSyncBatchItemEntity> items = contactIds.stream()
                .map(contactId -> ContactSyncBatchItemEntity.builder()
                        .id(UUID.randomUUID())
                        .batchId(batchId)
                        .crmContactId(contactId)
                        .status(ContactSyncItemStatus.QUEUED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        itemRepository.saveAll(items);

        List<ContactSyncMessage> messages = items.stream()
                .map(item -> new ContactSyncMessage(batchId, item.getId(), item.getCrmContactId()))
                .toList();
        Set<UUID> enqueuedItems = contactSyncQueue.enqueue(messages);
        List<ContactSyncBatchItemEntity> failedToEnqueue = items.stream()
                .filter(item -> !enqueuedItems.contains(item.getId()))
                .toList();
        failedToEnqueue.forEach(item -> {
            item.setStatus(ContactSyncItemStatus.FAILED);
            item.setLastError("Unable to enqueue contact sync message");
            item.setUpdatedAt(Instant.now());
        });
        if (!failedToEnqueue.isEmpty()) {
            itemRepository.saveAll(failedToEnqueue);
        }

        return getStatus(batchId);
    }

    public ContactSyncBatchStatusResponse getStatus(UUID batchId) {
        ContactSyncBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Contact sync batch not found: " + batchId
                ));

        return new ContactSyncBatchStatusResponse(
                batchId,
                batch.getTotalCount(),
                count(batchId, ContactSyncItemStatus.QUEUED),
                count(batchId, ContactSyncItemStatus.PROCESSING),
                count(batchId, ContactSyncItemStatus.SUCCEEDED),
                count(batchId, ContactSyncItemStatus.FAILED),
                batch.getCreatedAt()
        );
    }

    public Page<ContactSyncBatchItemResponse> getItems(UUID batchId, int page, int size) {
        if (page < 0 || size < 1 || size > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 500");
        }
        if (!batchRepository.existsById(batchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact sync batch not found: " + batchId);
        }
        return itemRepository.findByBatchId(
                        batchId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")
                                .and(Sort.by(Sort.Direction.ASC, "id")))
                )
                .map(item -> new ContactSyncBatchItemResponse(
                        item.getId(),
                        item.getCrmContactId(),
                        item.getStatus(),
                        item.getLastError(),
                        item.getUpdatedAt()
                ));
    }

    private long count(UUID batchId, ContactSyncItemStatus status) {
        return itemRepository.countByBatchIdAndStatus(batchId, status);
    }
}
