package com.modernice.integration.queue;

public record ContactSyncDelivery(ContactSyncMessage message, String receiptHandle, int receiveCount) {}
