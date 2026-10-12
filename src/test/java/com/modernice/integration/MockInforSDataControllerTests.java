package com.modernice.integration;

import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.InforSDataProperties;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.controller.MockInforSDataController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class MockInforSDataControllerTests {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        MockInforCrmClient mockInforCrmClient = new MockInforCrmClient();
        mockInforCrmClient.initializeDefaultContacts();
        client = WebTestClient.bindToController(new MockInforSDataController(
                mockInforCrmClient,
                new InforSDataMapper(new InforSDataProperties())
        )).build();
    }

    @Test
    void servesAccountAndRelatedAddressUsingSDataShape() {
        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$key").isEqualTo("A500")
                .jsonPath("$.AccountName").isEqualTo("Modern Ice Customer")
                .jsonPath("$.Address.$key").isEqualTo("account-A500");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('account-A500')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.City").isEqualTo("Cincinnati")
                .jsonPath("$.State").isEqualTo("OH")
                .jsonPath("$.PostalCode").isEqualTo("45202");
    }

    @Test
    void updatesAccountPropertiesWithoutRemovingItsExistingHubSpotId() {
        client.put()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')?format=json")
                .bodyValue(java.util.Map.of("HubSpotCompanyId", "50000001"))
                .exchange()
                .expectStatus().isOk();

        client.put()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')?format=json")
                .bodyValue(java.util.Map.of("AccountName", "Modern Ice Updated"))
                .exchange()
                .expectStatus().isOk();

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('account-A500')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.Address1").isEqualTo("123 Main Street")
                .jsonPath("$.City").isEqualTo("Cincinnati");

        client.put()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('account-A500')?format=json")
                .bodyValue(java.util.Map.of("City", "Cincy"))
                .exchange()
                .expectStatus().isOk();

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.HubSpotCompanyId").isEqualTo("50000001")
                .jsonPath("$.AccountName").isEqualTo("Modern Ice Updated");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('account-A500')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.City").isEqualTo("Cincy")
                .jsonPath("$.Address1").isEqualTo("123 Main Street")
                .jsonPath("$.State").isEqualTo("OH")
                .jsonPath("$.PostalCode").isEqualTo("45202");
    }
}
