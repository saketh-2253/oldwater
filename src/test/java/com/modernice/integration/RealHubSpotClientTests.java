package com.modernice.integration;

import com.modernice.integration.connector.hubspot.HubSpotProperties;
import com.modernice.integration.connector.hubspot.RealHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealHubSpotClientTests {

    private final List<ClientRequest> requests = new ArrayList<>();

    private RealHubSpotClient client(java.util.function.Function<ClientRequest, ClientResponse> responder) {
        ExchangeFunction exchange = request -> {
            requests.add(request);
            return Mono.just(responder.apply(request));
        };
        HubSpotProperties properties = new HubSpotProperties();
        properties.setAccessToken("test-token");
        properties.setMaxRetries(0);
        return new RealHubSpotClient(WebClient.builder().exchangeFunction(exchange), properties);
    }

    private ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void getContactReturnsPropertiesAndSendsBearerToken() {
        var client = client(r -> json(HttpStatus.OK, "{\"id\":\"1\",\"properties\":{\"email\":\"a@b.com\"}}"));

        Map<String, Object> result = client.getContact("1").block();

        assertEquals("a@b.com", result.get("email"));
        assertEquals("GET", requests.get(0).method().name());
        assertEquals("/crm/v3/objects/contacts/1", requests.get(0).url().getPath());
        assertEquals("Bearer test-token", requests.get(0).headers().getFirst("Authorization"));
    }

    @Test
    void getContactIsEmptyWhenNotFound() {
        var client = client(r -> json(HttpStatus.NOT_FOUND, "{\"status\":\"error\"}"));

        assertTrue(client.getContact("missing").blockOptional().isEmpty());
    }

    @Test
    void createContactReturnsAssignedId() {
        var client = client(r -> json(HttpStatus.CREATED, "{\"id\":\"777\",\"properties\":{}}"));

        String id = client.createContact(new HubSpotContactRequest(Map.of("email", "a@b.com"))).block();

        assertEquals("777", id);
        assertEquals("POST", requests.get(0).method().name());
        assertEquals("/crm/v3/objects/contacts", requests.get(0).url().getPath());
    }

    @Test
    void upsertContactCreatesWhenPatchReturnsNotFound() {
        var client = client(r -> "PATCH".equals(r.method().name())
                ? json(HttpStatus.NOT_FOUND, "{}")
                : json(HttpStatus.CREATED, "{\"id\":\"9\"}"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("email", "a@b.com");
        props.put("phone", null);

        client.upsertContact("missing", new HubSpotContactRequest(props)).block();

        assertEquals(2, requests.size());
        assertEquals("PATCH", requests.get(0).method().name());
        assertEquals("POST", requests.get(1).method().name());
    }

    @Test
    void serverErrorsPropagate() {
        var client = client(r -> json(HttpStatus.BAD_REQUEST, "{\"message\":\"bad\"}"));

        assertThrows(Exception.class,
                () -> client.updateCompany("5", new com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest(Map.of("name", "x"))).block());
    }

    @Test
    void missingTokenFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new RealHubSpotClient(WebClient.builder(), new HubSpotProperties()));
    }
}
