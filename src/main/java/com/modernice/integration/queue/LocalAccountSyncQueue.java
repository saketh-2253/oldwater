package com.modernice.integration.queue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Profile("local")
public class LocalAccountSyncQueue implements AccountSyncQueue {

    private final BlockingQueue<AccountSyncMessage> messages = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, AccountSyncMessage> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> receiveCounts = new ConcurrentHashMap<>();

    @Override
    public Set<UUID> enqueue(List<AccountSyncMessage> messages) {
        this.messages.addAll(messages);
        return messages.stream().map(AccountSyncMessage::itemId).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public List<AccountSyncDelivery> receive(int maxMessages) {
        List<AccountSyncDelivery> deliveries = new ArrayList<>();
        for (int i = 0; i < maxMessages; i++) {
            AccountSyncMessage message = messages.poll();
            if (message == null) {
                break;
            }
            String receipt = UUID.randomUUID().toString();
            inFlight.put(receipt, message);
            deliveries.add(new AccountSyncDelivery(
                    message,
                    receipt,
                    receiveCounts.merge(message.itemId(), 1, Integer::sum)
            ));
        }
        return deliveries;
    }

    @Override
    public void acknowledge(AccountSyncDelivery delivery) {
        inFlight.remove(delivery.receiptHandle());
    }

    @Override
    public void retry(AccountSyncDelivery delivery) {
        AccountSyncMessage message = inFlight.remove(delivery.receiptHandle());
        if (message != null) {
            messages.add(message);
        }
    }

    @Override
    public void deadLetter(AccountSyncDelivery delivery, String reason) {
        acknowledge(delivery);
    }
}
