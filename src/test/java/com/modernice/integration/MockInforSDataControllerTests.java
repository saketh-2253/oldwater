package com.modernice.integration;

import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.InforSDataProperties;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.controller.MockInforSDataController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

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
    void createdGeneratedContactIdCanBeReadBackFromSData() {
        EntityExchangeResult<Map<String, Object>> created = client.post()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/contacts?format=json")
                .bodyValue(Map.of(
                        "FirstName", "Jane",
                        "LastName", "Doe",
                        "Email", "jane.doe@example.com",
                        "Address", Map.of("City", "Columbus", "State", "OH")
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult();
        String contactId = String.valueOf(Objects.requireNonNull(created.getResponseBody()).get("$key"));

        assertThat(contactId).startsWith("C-MOCK-");
        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/contacts('{contactId}')?format=json", contactId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$key").isEqualTo(contactId)
                .jsonPath("$.FirstName").isEqualTo("Jane")
                .jsonPath("$.LastName").isEqualTo("Doe");
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
                .jsonPath("$.Address.$key").isEqualTo("account-A500")
                .jsonPath("$.Address.$uuid").isEqualTo("00000000-0000-0000-0000-000000000000")
                .jsonPath("$.Address.$url").value(value ->
                        assertThat(value.toString()).endsWith("/addresses('account-A500')"))
                .jsonPath("$.Address.$lookup").value(value ->
                        assertThat(value.toString()).endsWith("/addresses?format=json"))
                .jsonPath("$.Addresses.$url").value(value ->
                        assertThat(value.toString()).endsWith("/accounts('A500')/Addresses"))
                .jsonPath("$.Contacts.$url").value(value ->
                        assertThat(value.toString())
                                .endsWith("/contacts?format=json&where=Account.Id%20eq%20'A500'"));

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
    void accountCollectionReturnsSDataFeedAndRelationshipLinks() {
        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts?startIndex=1&count=50&format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$totalResults").isEqualTo(1)
                .jsonPath("$.$startIndex").isEqualTo(1)
                .jsonPath("$.$itemsPerPage").isEqualTo(1)
                .jsonPath("$.$resources[0].$key").isEqualTo("A500")
                .jsonPath("$.$resources[0].Address.$uuid")
                .isEqualTo("00000000-0000-0000-0000-000000000000")
                .jsonPath("$.$resources[0].Contacts.$url").value(value ->
                        assertThat(value.toString())
                                .endsWith("/contacts?format=json&where=Account.Id%20eq%20'A500'"));
    }

    @Test
    void accountContactsNavigationListsContactsRelatedByAccountId() {
        client.post()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/contacts?format=json")
                .bodyValue(Map.of(
                        "FirstName", "Jane",
                        "LastName", "Doe",
                        "Account", Map.of("$key", "A500")
                ))
                .exchange()
                .expectStatus().isCreated();

        client.get()
                .uri(URI.create("http://localhost/mock/infor-sdata/sdata/slx/dynamic/-/contacts"
                        + "?format=json&where=Account.Id%20eq%20'A500'"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$totalResults").isEqualTo(2)
                .jsonPath("$.$resources[?(@.FirstName == 'John')].$key").exists()
                .jsonPath("$.$resources[?(@.FirstName == 'Jane')].$key").exists();
    }

    @Test
    void addressLookupReturnsSDataFeedContainingAddressEntries() {
        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$resources").isArray()
                .jsonPath("$.$resources[?(@.$key == 'account-A500')].City").isEqualTo("Cincinnati")
                .jsonPath("$.$resources[?(@.$key == 'contact-103')].City").isEqualTo("Cincinnati");
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

    @Test
    void putCreatesContactAtTheRequestedKeyWhenItDoesNotExist() {
        client.put()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/contacts('C-MOCK-batch-test')?format=json")
                .bodyValue(java.util.Map.of(
                        "FirstName", "Jane",
                        "LastName", "Doe",
                        "Email", "jane.doe@example.com",
                        "Address", java.util.Map.of("City", "Columbus", "State", "OH")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$key").isEqualTo("C-MOCK-batch-test")
                .jsonPath("$.FirstName").isEqualTo("Jane")
                .jsonPath("$.Address.$key").isEqualTo("contact-C-MOCK-batch-test");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/contacts('C-MOCK-batch-test')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.LastName").isEqualTo("Doe")
                .jsonPath("$.Email").isEqualTo("jane.doe@example.com");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('contact-C-MOCK-batch-test')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.City").isEqualTo("Columbus")
                .jsonPath("$.State").isEqualTo("OH");
    }

    @Test
    void putCreatesAccountAtTheRequestedKeyWhenItDoesNotExist() {
        client.put()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A-MOCK-batch-test')?format=json")
                .bodyValue(java.util.Map.of(
                        "ExternalAccountNumber", "POSTMAN-CUSTOMER-001",
                        "AccountName", "Postman Test Account",
                        "Address", java.util.Map.of(
                                "Address1", "123 Main Street",
                                "City", "Columbus",
                                "State", "OH",
                                "PostalCode", "43215"
                        )
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.$key").isEqualTo("A-MOCK-batch-test")
                .jsonPath("$.AccountName").isEqualTo("Postman Test Account");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A-MOCK-batch-test')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ExternalAccountNumber").isEqualTo("POSTMAN-CUSTOMER-001")
                .jsonPath("$.Address.$key").isEqualTo("account-A-MOCK-batch-test");

        client.get()
                .uri("/mock/infor-sdata/sdata/slx/dynamic/-/addresses('account-A-MOCK-batch-test')?format=json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.City").isEqualTo("Columbus")
                .jsonPath("$.PostalCode").isEqualTo("43215");
    }
}
