package com.modernice.integration.connector.infor.crm;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class NoSDataAuthenticationProvider implements SDataAuthenticationProvider {

    @Override
    public String type() {
        return "none";
    }

    @Override
    public void apply(HttpHeaders headers) {
    }
}
