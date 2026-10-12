package com.modernice.integration.connector.infor.crm;

import org.springframework.http.HttpHeaders;

public interface SDataAuthenticationProvider {
    String type();

    void apply(HttpHeaders headers);
}
