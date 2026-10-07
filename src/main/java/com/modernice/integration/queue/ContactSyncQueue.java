package com.modernice.integration.queue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContactSyncQueue {
    Set<UUID> enqueue(List<ContactSyncMessage> messages);
    List<ContactSyncDelivery> receive(int maxMessages);
    void acknowledge(ContactSyncDelivery delivery);
    void retry(ContactSyncDelivery delivery);
    void deadLetter(ContactSyncDelivery delivery, String reason);
}
