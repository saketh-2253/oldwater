package com.modernice.integration.workflow;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException(String crmContactId) {
        super("INFOR CRM contact not found: " + crmContactId);
    }
}
