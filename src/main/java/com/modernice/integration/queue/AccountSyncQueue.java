package com.modernice.integration.queue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AccountSyncQueue {
    Set<UUID> enqueue(List<AccountSyncMessage> messages);
    List<AccountSyncDelivery> receive(int maxMessages);
    void acknowledge(AccountSyncDelivery delivery);
    void retry(AccountSyncDelivery delivery);
    void deadLetter(AccountSyncDelivery delivery, String reason);
}
