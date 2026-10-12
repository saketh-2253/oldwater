package com.modernice.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.InforSDataProperties;
import com.modernice.integration.connector.infor.crm.NoSDataAuthenticationProvider;
import com.modernice.integration.connector.infor.crm.SDataAuthenticationSelector;
import com.modernice.integration.connector.infor.crm.SDataInforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SDataInforCrmClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, String> requestedQueries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> putBodies = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService serverExecutor;
    private SDataInforCrmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/sdata/slx/dynamic/-/", this::handleRequest);
        server.start();

        InforSDataProperties properties = new InforSDataProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/sdata/slx/dynamic/-");
        InforSDataMapper mapper = new InforSDataMapper(properties);
        SDataAuthenticationSelector authentication = new SDataAuthenticationSelector(
                properties,
                List.of(new NoSDataAuthenticationProvider())
        );
        client = new SDataInforCrmClient(
                WebClient.builder(),
                properties,
                mapper,
                authentication,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void getsContactAndResolvesItsSDataAddressAndAccountReferences() {
        InforContactDto contact = client.getContact("C1").block();

        assertThat(contact).isNotNull();
        assertThat(contact.crmContactId()).isEqualTo("C1");
        assertThat(contact.crmAccountId()).isEqualTo("A1");
        assertThat(contact.miCustomerId()).isEqualTo("12345");
        assertThat(contact.city()).isEqualTo("Cincinnati");
        assertThat(contact.state()).isEqualTo("OH");
        assertThat(requestedQueries).containsKeys(
                "/sdata/slx/dynamic/-/contacts('C1')",
                "/sdata/slx/dynamic/-/addresses('addr-C1')",
                "/sdata/slx/dynamic/-/accounts('A1')"
        );
    }

    @Test
    void updatesAnAccountUsingItsSDataKeyAndJsonFormat() {
        client.updateHubSpotCompanyId("A1", "50000001").block();

        assertThat(requestedQueries.get("/sdata/slx/dynamic/-/accounts('A1')")).isEqualTo("format=json");
        assertThat(putBodies.get("/sdata/slx/dynamic/-/accounts('A1')"))
                .containsEntry("HubSpotCompanyId", "50000001");
    }

    @Test
    void updatesAddressAsItsOwnSDataResource() {
        client.upsertAccount(new InforAccountDto(
                "12345", "A1", "Modern Ice", "1 Main Street", null, "Cincinnati",
                "OH", "45202", "5135551000", null, "Customer", "Active",
                "100", "U1", "DI", "U2", "50000001"
        )).block();

        assertThat(putBodies.get("/sdata/slx/dynamic/-/accounts('A1')"))
                .containsEntry("AccountName", "Modern Ice")
                .doesNotContainKey("Address");
        assertThat(putBodies.get("/sdata/slx/dynamic/-/addresses('addr-A1')"))
                .containsEntry("City", "Cincinnati")
                .containsEntry("PostalCode", "45202");
    }

    @Test
    void returnsAnEmptyResultWhenSDataCannotFindAContact() {
        assertThat(client.getContact("missing").block()).isNull();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedQueries.put(path, exchange.getRequestURI().getRawQuery());
        if ("PUT".equals(exchange.getRequestMethod())) {
            Map<String, Object> body = objectMapper.readValue(
                    exchange.getRequestBody(),
                    new TypeReference<>() {}
            );
            putBodies.put(path, body);
            respond(exchange, 200, "{}");
        } else if (path.endsWith("/contacts('missing')")) {
            respond(exchange, 404, "{}");
        } else if (path.endsWith("/contacts('C1')")) {
            respond(exchange, 200, """
                    {
                      "$key": "C1",
                      "FirstName": "John",
                      "LastName": "Smith",
                      "AccountName": "Modern Ice",
                      "Account": {"$key": "A1"},
                      "Address": {"$key": "addr-C1"},
                      "Email": "john@example.com"
                    }
                    """);
        } else if (path.endsWith("/addresses('addr-C1')")) {
            respond(exchange, 200, """
                    {"$key":"addr-C1","City":"Cincinnati","State":"OH"}
                    """);
        } else if (path.endsWith("/accounts('A1')")) {
            respond(exchange, 200, """
                    {
                      "$key":"A1",
                      "ExternalAccountNumber":"12345",
                      "Address":{"$key":"addr-A1"}
                    }
                    """);
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
