package com.modernice.integration.queue;

public record AccountSyncDelivery(AccountSyncMessage message, String receiptHandle, int receiveCount) {}
