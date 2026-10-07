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
import java.util.stream.Collectors;

@Component
@Profile("local")
public class LocalContactSyncQueue implements ContactSyncQueue {

    private final BlockingQueue<ContactSyncMessage> messages = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, ContactSyncMessage> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> receiveCounts = new ConcurrentHashMap<>();

    @Override
    public Set<UUID> enqueue(List<ContactSyncMessage> messages) {
        this.messages.addAll(messages);
        return messages.stream().map(ContactSyncMessage::itemId).collect(Collectors.toSet());
    }

    @Override
    public List<ContactSyncDelivery> receive(int maxMessages) {
        List<ContactSyncDelivery> deliveries = new ArrayList<>();
        for (int i = 0; i < maxMessages; i++) {
            ContactSyncMessage message = messages.poll();
            if (message == null) {
                break;
            }
            String receipt = UUID.randomUUID().toString();
            inFlight.put(receipt, message);
            int receiveCount = receiveCounts.merge(message.itemId(), 1, Integer::sum);
            deliveries.add(new ContactSyncDelivery(message, receipt, receiveCount));
        }
        return deliveries;
    }

    @Override
    public void acknowledge(ContactSyncDelivery delivery) {
        inFlight.remove(delivery.receiptHandle());
    }

    @Override
    public void retry(ContactSyncDelivery delivery) {
        ContactSyncMessage message = inFlight.remove(delivery.receiptHandle());
        if (message != null) {
            messages.add(message);
        }
    }

    @Override
    public void deadLetter(ContactSyncDelivery delivery, String reason) {
        acknowledge(delivery);
    }
}
