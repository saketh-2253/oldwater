package com.modernice.integration.connector.infor.crm;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SDataAuthenticationSelector {

    private final SDataAuthenticationProvider provider;

    public SDataAuthenticationSelector(
            InforSDataProperties properties,
            List<SDataAuthenticationProvider> providers) {
        String configuredType = properties.getAuth().getType();
        this.provider = providers.stream()
                .filter(candidate -> candidate.type().equalsIgnoreCase(configuredType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No SData authentication provider is registered for type: " + configuredType
                ));
    }

    public void apply(HttpHeaders headers) {
        provider.apply(headers);
    }
}
