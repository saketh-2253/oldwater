package com.modernice.integration.workflow;

import com.modernice.integration.controller.dto.AccountSyncBatchItemResponse;
import com.modernice.integration.controller.dto.AccountSyncBatchStatusResponse;
import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.AccountSyncBatchEntity;
import com.modernice.integration.persistence.entity.AccountSyncBatchItemEntity;
import com.modernice.integration.persistence.repository.AccountSyncBatchItemRepository;
import com.modernice.integration.persistence.repository.AccountSyncBatchRepository;
import com.modernice.integration.queue.AccountSyncMessage;
import com.modernice.integration.queue.AccountSyncQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountSyncBatchService {

    private final AccountSyncQueue accountSyncQueue;
    private final AccountSyncBatchRepository batchRepository;
    private final AccountSyncBatchItemRepository itemRepository;

    public AccountSyncBatchStatusResponse submit(List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty() || accountIds.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide between 1 and 10000 account IDs");
        }
        if (accountIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account IDs must be non-blank and at most 255 characters");
        }
        if (new HashSet<>(accountIds).size() != accountIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account IDs must not contain duplicates");
        }

        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        batchRepository.save(AccountSyncBatchEntity.builder()
                .id(batchId)
                .totalCount(accountIds.size())
                .createdAt(now)
                .build());
        List<AccountSyncBatchItemEntity> items = accountIds.stream()
                .map(accountId -> AccountSyncBatchItemEntity.builder()
                        .id(UUID.randomUUID())
                        .batchId(batchId)
                        .accountId(accountId)
                        .status(ContactSyncItemStatus.QUEUED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        itemRepository.saveAll(items);
        Set<UUID> enqueued = accountSyncQueue.enqueue(items.stream()
                .map(item -> new AccountSyncMessage(batchId, item.getId(), item.getAccountId()))
                .toList());

        List<AccountSyncBatchItemEntity> failedToEnqueue = items.stream()
                .filter(item -> !enqueued.contains(item.getId()))
                .toList();
        failedToEnqueue.forEach(item -> {
            item.setStatus(ContactSyncItemStatus.FAILED);
            item.setLastError("Unable to enqueue account sync message");
            item.setUpdatedAt(Instant.now());
        });
        if (!failedToEnqueue.isEmpty()) {
            itemRepository.saveAll(failedToEnqueue);
        }
        return getStatus(batchId);
    }

    public AccountSyncBatchStatusResponse getStatus(UUID batchId) {
        AccountSyncBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Account sync batch not found: " + batchId));
        return new AccountSyncBatchStatusResponse(
                batchId,
                batch.getTotalCount(),
                count(batchId, ContactSyncItemStatus.QUEUED),
                count(batchId, ContactSyncItemStatus.PROCESSING),
                count(batchId, ContactSyncItemStatus.SUCCEEDED),
                count(batchId, ContactSyncItemStatus.FAILED),
                batch.getCreatedAt()
        );
    }

    public Page<AccountSyncBatchItemResponse> getItems(UUID batchId, int page, int size) {
        if (page < 0 || size < 1 || size > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 500");
        }
        if (!batchRepository.existsById(batchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account sync batch not found: " + batchId);
        }
        return itemRepository.findByBatchId(
                        batchId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")
                                .and(Sort.by(Sort.Direction.ASC, "id")))
                )
                .map(item -> new AccountSyncBatchItemResponse(
                        item.getId(),
                        item.getAccountId(),
                        item.getStatus(),
                        item.getLastError(),
                        item.getUpdatedAt()
                ));
    }

    private long count(UUID batchId, ContactSyncItemStatus status) {
        return itemRepository.countByBatchIdAndStatus(batchId, status);
    }
}
