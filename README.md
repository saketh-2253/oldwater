# Modern Ice Integration Starter

Starter Spring Boot project for the INFOR CRM / INFOR ERP / HubSpot integration.

## Included

- Canonical models for Contact, Company, Deal, Order, Invoice, and LineItem
- INFOR Contact DTO + mapper
- Configurable INFOR SData client for Contact and Account reads/updates
- Local SData-compatible mock endpoints for Contact, Account, and related Address resources
- HubSpot Contact request mapper
- ERP Territory value mapping examples from the supplied mapping documents
- Contact identity persistence
- First working Contact sync workflow
- Mock INFOR and HubSpot clients for the `local` profile
- PostgreSQL + Flyway
- Docker + Docker Compose

## Contact sync

`INFOR Contact -> Canonical Contact -> HubSpot Contact`

Sync one contact:

```http
POST /api/sync/contacts/C1001
```

Submit a batch of contact IDs:

```http
POST /api/sync/contacts/batch
Content-Type: application/json

{
  "contactIds": ["C1001", "C1002", "C1003"]
}
```

The batch endpoint returns `202 Accepted` and a `batchId` after queue submission; it does not wait for the contact syncs to finish. Each ID is processed as its own queue message, so the request uses the same path for small and large batches. The maximum request size is 10,000 unique IDs; SQS publish calls are grouped in chunks of at most 10 messages. Check progress using the returned status URL:

```http
GET /api/sync/batches/{batchId}
```

The response reports queued, processing, succeeded, and failed counts. Use `GET /api/sync/batches/{batchId}/items?page=0&size=100` to inspect each contact's status and any failure detail. Failed contact syncs are retried up to the configured attempt count and then sent to the configured dead-letter queue.

With the default `local` profile, an in-memory queue and mock CRM/HubSpot clients are used. The queue is not durable and is intended only for local development.

## INFOR SData

Contact and account sync calls the SData client through the `InforCrmClient` interface. In the default `local` profile, that client calls the SData-shaped mock endpoints hosted by this application; the mock stores its data in memory and is seeded with contact `103` and account `A500`.

Example local mock requests:

```http
GET http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/contacts('103')?format=json
GET http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')?format=json
GET http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/accounts?startIndex=1&count=50&format=json
```

The mock follows the supplied SData examples for JSON entries and feeds. Account collection requests return a feed with `$resources`, `$totalResults`, `$startIndex`, and `$itemsPerPage`. Account and Contact `Address` references include `$key`, `$url`, `$uuid`, and `$lookup`; account resources expose an `Addresses` navigation link and a `Contacts` link filtered by `Account.Id`. The linked `GET /contacts?...where=Account.Id%20eq%20'accountId'`, `GET /addresses?format=json`, and `GET /accounts('{accountId}')/Addresses` endpoints return SData feeds with a `$resources` array. Links are generated from the incoming request host and point back to this local mock. Other live Infor navigation collections not represented by the mock's data model are intentionally not emitted. The middleware's existing `/api/infor/...` test endpoints now go through the SData client rather than accessing the mock store directly.

To point the client at a real SData server later, set `INFOR_SDATA_BASE_URL` to the resource-kind root ending in `/sdata/slx/dynamic/-` (or the corresponding root for your Infor deployment). No sync workflow or client code changes are needed to switch the URL:

```powershell
$env:INFOR_SDATA_BASE_URL = "https://infor-host.example.com/sdata/slx/dynamic/-"
```

The URL defaults to the local mock root. Authentication is selected by `INFOR_SDATA_AUTH_TYPE`; it currently defaults to `none`, which is intended for the local mock. The client delegates authentication to an `SDataAuthenticationProvider`, so a provider for the real Infor authentication scheme can be registered and selected by configuration without changing the SData client. No live authentication provider is included yet.

The SData documents' standard field names are mapped to the existing DTOs. The non-standard/customer-specific property names can be overridden with these environment variables if the real SData schema uses different names:

| Variable | Default SData property |
| --- | --- |
| `INFOR_SDATA_CONTACT_CUSTOMER_ID_FIELD` | `MiCustomerId` |
| `INFOR_SDATA_CONTACT_ERP_TERRITORY_FIELD` | `ErpTerritory` |
| `INFOR_SDATA_CONTACT_ERP_CONTACT_NUMBER_FIELD` | `ErpExtId` |
| `INFOR_SDATA_CONTACT_HUBSPOT_ID_FIELD` | `HubSpotRecordId` |
| `INFOR_SDATA_ACCOUNT_CUSTOMER_ID_FIELD` | `ExternalAccountNumber` |
| `INFOR_SDATA_ACCOUNT_HUBSPOT_ID_FIELD` | `HubSpotCompanyId` |

## Account / Company sync

The mapping workbook describes INFOR CRM Account ↔ HubSpot Company as a two-way mapping. The account ID is the unique source identifier. The implemented property mapping is:

| INFOR account field | HubSpot company property |
| --- | --- |
| Customer ID | `customer_id` |
| Account name | `name` |
| Address 1 / Address 2 | `address` / `address2` |
| City / State / Postal Code | `city` / `state` / `zip` |
| Main Phone | `phone` |
| Sub-Type / Status | `subtype` / `status` |
| ERP Territory / Account Manager | `erp_territory` / `account_manager` |
| Customer Type / Owner | `customer_type` / `infor_owner` |
| Account ID | `infor_account_id` |

The mapping workbook does not specify a HubSpot property for the INFOR `Type` field, so that field is retained by the INFOR model but not sent to HubSpot.

Sync one INFOR account to HubSpot:

```http
POST /api/sync/accounts/{accountId}
```

Sync one HubSpot company to INFOR:

```http
POST /api/sync/hubspot/companies/{hubSpotCompanyId}
```

The alias `POST /api/hubspot/companies/{hubSpotCompanyId}/sync-to-infor` is also available. Reverse sync requires `infor_account_id` on the HubSpot company or an existing saved account identity mapping.

Submit a batch of INFOR account IDs for background sync:

```http
POST /api/sync/accounts/batch
Content-Type: application/json

{
  "accountIds": ["A500"]
}
```

The local mock includes account `A500`. The API returns `202 Accepted` with a batch ID and status URL. Check batch counts at `GET /api/sync/account-batches/{batchId}` and individual item results at `GET /api/sync/account-batches/{batchId}/items?page=0&size=100`. The account batch has the same 10,000 unique-ID maximum, SQS batches of up to 10 messages, background retries, and dead-letter handling as contact batches. The local mock seeds `A500` with territory `100` and customer type code `DI`; the mapper converts these to the configured HubSpot dropdown labels and reverses known labels on HubSpot-to-INFOR sync. When updating an existing account through `POST /api/infor/accounts/{accountId}`, a `null` `hubSpotCompanyId` in the request preserves the ID already stored in the mock.

## Deal, Order, Invoice, and Line Item mappings

The supplied mapping workbook places Opportunities and HubSpot Deals in scope for two-way mapping. Infor ERP Orders and Invoices map one-way to HubSpot Orders, and their line items map one-way to HubSpot Line Items. `HubSpotDealMapper`, `HubSpotOrderMapper`, `HubSpotInvoiceMapper`, and `HubSpotLineItemMapper` now translate the existing canonical models into HubSpot property maps. The Deal reverse mapper only reads fields whose rows are explicitly marked two-way in the workbook.

| Canonical Deal field | HubSpot property |
| --- | --- |
| Opportunity name / account ID | `dealname` / `account_id` |
| Account manager | `account_maanger` |
| Estimated / actual close date | `est_close_date` / `infor_close_date` |
| Potential / actual amount / probability | `potential_amount` / `actual_amount` / `infor_probability` |
| Stage / status / reason won | `dealstage` / `infor_status` / `closed_won_reason` |
| Opportunity ID | `infor_opportunity_id` |
| Lead source / owner / opportunity type / description | `lead_source` / `infor_owner` / `opportunity_type` / `description` |

| Canonical Order field | HubSpot Order property |
| --- | --- |
| Entered date / order number / transaction type / stage | `entered_date` / `order_number` / `transaction_type` / `hs_pipeline_stage` |
| Customer name / quantity ordered / reference / taken by | `customer_name` / `quantity_ordered` / `reference` / `taken_by` |
| Customer number / ship-to name / division | `customer_number` / `ship_to_name` / `division` |
| Shipped date / invoiced date | `shipped_date` / `invoice_date` |
| Invoiced / ordered / shipped amount | `invoiced_amount` / `ordered_amount` / `shipped_amount` |
| Inside/outside sales rep IDs and names; taken-by name | `inside_sales_rep`, `inside_sales_rep_name`, `outside_sales_rep`, `outside_sales_rep_name`, `taken_by_name` |
| Vendor return authorization / order notes / customer notes | `vendor_return_authorization` / `order_notes` / `customer_notes` |

| Canonical Invoice field | HubSpot Order property |
| --- | --- |
| Invoice number / order number / account ID / customer name | `invoice_number` / `order_number` / `account_id` / `customer_name` |
| Invoice date / amount | `invoice_date` / `invoiced_amount` |

| Canonical LineItem field | HubSpot Line Item property |
| --- | --- |
| Product SKU / name / unit price / quantity | `hs_sku` / `name` / `price` / `quantity` |
| Order suffix / unit cost / profit / product category / order number | `order_suffix` / `unit_cost` / `profit` / `line_prodcat` / `order_number` |

The workbook explicitly supplies only some HubSpot internal names; most unfilled cells are marked “Implementation Specialist to Complete.” The code uses the explicit internal names where supplied and conventional/native names for the remaining fields. Confirm every inferred name against the target HubSpot portal before enabling API writes. In particular, the workbook spells the Deal account-manager internal name `account_maanger`; it is retained as written and must be corrected in both the portal and mapper if that spelling is not intentional. The workbook also does not provide the HubSpot option IDs for deal stages or define a standalone Invoice object, so stage values are passed through and invoice properties are mapped onto the HubSpot Order object.

### Runnable local business workflows

`local` profile seeds mock opportunity `OPP-1001`, order `ORD-1001`, invoice `INV-1001`, and product `SKU-ICE-01`. Source test records can be read or replaced using `GET` / `PUT` at `/api/infor/opportunities/{id}`, `/api/infor/orders/{id}`, `/api/infor/invoices/{id}`, and `/api/infor/products/{sku}`. PUT JSON uses the `InforOpportunityDto`, `InforOrderDto`, `InforInvoiceDto`, and `InforProductDto` property names; IDs in the body must match the URL.

The local SData mock also supports adding business records without replacing its seeded data. Use `POST` on a collection to create a record with a generated mock ID, `GET` on the collection to list seeded and added records, and `GET` / `PUT` on an item to read or update it:

| Resource | Collection |
| --- | --- |
| Opportunities | `/mock/infor-sdata/sdata/slx/dynamic/-/opportunities` |
| Orders | `/mock/infor-sdata/sdata/slx/dynamic/-/orders` |
| Invoices | `/mock/infor-sdata/sdata/slx/dynamic/-/invoices` |
| Products | `/mock/infor-sdata/sdata/slx/dynamic/-/products` |

Collection GETs return SData feeds with `$resources` and support `startIndex` and `count`. Resource entries have absolute `$url` / `$lookup` links. Relationship fields use SData references with `$key`, `$url`, `$uuid`, and `$lookup`, rather than returning the foreign ID as the relationship itself. For example, an Opportunity uses `Account` and exposes navigable `Contacts`, `Products`, and `SalesOrders` child collections. An Order uses `Account` and gives each embedded line item a `Product` reference; an Invoice uses `Account`, `SalesOrder`, and line-item `Product` references. The local Order model keeps its account link key separately from `customerNumber`. Product itself is a standalone resource identified by `productSku`; products are linked from opportunity collections and order/invoice line items.

For example, create an opportunity with `POST /mock/infor-sdata/sdata/slx/dynamic/-/opportunities?format=json` and a body like `{"opportunityName":"New equipment","Account":{"$key":"A500"},"Contacts":[{"$key":"103"}],"Products":[{"$key":"SKU-ICE-01"}],"SalesOrders":[{"$key":"ORD-1001"}],"potentialAmount":30000.00}`. The three association lists are explicit: use existing contact IDs, product SKUs, and order numbers. The returned opportunity includes `Contacts`, `Products`, and `SalesOrders` navigation links; follow them to receive paged SData feeds of the associated resources. A `PUT` containing one of these collection properties replaces that opportunity's association list, and `[]` clears it. References are checked against existing mock resources before the opportunity is saved. Create an order with a relationship-aware line item:

```json
{
  "customerName": "Postman customer",
  "customerNumber": "12345",
  "Account": { "$key": "A500" },
  "lineItems": [
    {
      "orderSuffix": "01",
      "name": "Commercial ice maker",
      "unitPrice": 1250.00,
      "quantity": 2,
      "Product": { "$key": "SKU-ICE-01" }
    }
  ]
}
```

An Invoice can refer to existing resources similarly: `{"Account":{"$key":"A500"},"SalesOrder":{"$key":"ORD-1001"},"customerName":"Modern Ice Customer","invoiceDate":"2026-10-14","amount":2500.00}`. A Product is created as its own resource with `POST /mock/infor-sdata/sdata/slx/dynamic/-/products?format=json` and fields such as `name`, `unitPrice`, `unitCost`, `profit`, and `productCategory`; its response includes its generated `productSku`, `$key`, and `$url`. Use the generated key in a line item's `Product.$key`. Partial `PUT` requests update only supplied fields; omitted fields, including saved HubSpot IDs, are retained. To explicitly clear a field, include it with a JSON `null` value. These mock records are in memory and reset when the application restarts; this does not enable CRUD against a live Infor SData server.

Trigger the workflows:

| Direction | Request |
| --- | --- |
| INFOR Opportunity → HubSpot Deal | `POST /api/sync/opportunities/{opportunityId}` |
| HubSpot Deal → INFOR Opportunity | `POST /api/sync/hubspot/deals/{hubSpotDealId}` |
| INFOR Order → HubSpot Order + associated Line Items | `POST /api/sync/orders/{orderNumber}` |
| INFOR Invoice → HubSpot Order + associated Line Items | `POST /api/sync/invoices/{invoiceNumber}` |
| INFOR Product → HubSpot Line Item | `POST /api/sync/products/{productSku}` |

These three POST sync routes now return the actual HubSpot object IDs in their JSON response. Order and Invoice responses include the parent `hubSpotObjectId` and every created/updated child `lineItemIds`; Product returns its HubSpot Line Item ID as `hubSpotObjectId` and saves that ID on the source product as `hubSpotLineItemId`.

Example Order response:

```json
{
  "sourceId": "ORD-1001",
  "hubSpotObjectType": "orders",
  "hubSpotObjectId": "700000001",
  "lineItemIds": ["700000002"]
}
```

Use the returned `lineItemIds` directly with `GET /api/hubspot/line-items/{id}`. For product sync, use `hubSpotObjectId` with the same GET route; verify the source product now carries the ID at `GET /api/infor/products/{sku}`. A resync reuses this saved ID instead of creating a duplicate.

Example local test run:

```http
POST http://localhost:8081/api/sync/opportunities/OPP-1001
POST http://localhost:8081/api/sync/orders/ORD-1001
POST http://localhost:8081/api/sync/invoices/INV-1001
POST http://localhost:8081/api/sync/products/SKU-ICE-01
```

To create or replace a local opportunity before syncing:

```http
PUT http://localhost:8081/api/infor/opportunities/OPP-1001
Content-Type: application/json

{
  "opportunityId": "OPP-1001",
  "hubSpotDealId": null,
  "opportunityName": "Modern Ice Equipment Upgrade",
  "accountId": "A500",
  "accountManager": "U738UA00000M",
  "estimatedCloseDate": "2026-12-31",
  "potentialAmount": 25000.00,
  "actualAmount": 0.00,
  "probability": 0.5,
  "stage": "appointmentscheduled",
  "status": "Open",
  "reasonWon": null,
  "leadSource": "Referral",
  "owner": "U738UA00002W",
  "opportunityType": "Equipment - Standard",
  "description": "Local sample opportunity"
}
```

Read mock HubSpot business records at `GET /api/hubspot/deals/{id}`, `GET /api/hubspot/orders/{id}`, or `GET /api/hubspot/line-items/{id}`. To simulate a HubSpot Deal edit, `POST /api/hubspot/deals/{id}` with a JSON property map (for example `{"dealname":"Updated opportunity","potential_amount":"30000.00"}`), then explicitly call the HubSpot-to-INFOR sync route. The deal must carry `infor_opportunity_id` or have an identity mapping before it can sync back.

INFOR DTOs are mapped to the existing canonical Deal, Order, Invoice, and LineItem models before entering workflow code. Persistent identities use the shared `integration_identity` table. Orders and invoices deliberately converge on one HubSpot Order record when their order number can be resolved; their individual line items are separately created/updated and associated with that Order. Product-only sync creates a HubSpot Line Item without an Order association.

The previously implemented Account ↔ Company workflow remains at `/api/sync/accounts/{accountId}` and `/api/sync/hubspot/companies/{hubSpotCompanyId}`. Contact routes and behavior were not changed.

### Real connector readiness

`RealInforBusinessClient` and `RealSalesHubSpotClient` are selected for every non-`local` profile (including `dev`, `tst`, `uat`, and `prod`). The HubSpot real client supports CRM v3 deal/order/line-item object CRUD, and the Infor real client exposes typed methods and configurable CRM, ERP, and Data Lake base URLs plus authentication placeholders. It intentionally returns explicit “not implemented” errors until the actual Infor endpoints, authentication, and payload contracts are confirmed. The HubSpot Order-to-Line Item association call likewise remains an explicit TODO because the target Order object and association type must be confirmed in the portal. These production limitations do not affect local mock workflows.

Configure the non-local INFOR connector placeholders using `INFOR_CRM_BASE_URL`, `INFOR_ERP_BASE_URL`, `INFOR_DATA_LAKE_BASE_URL`, `INFOR_BUSINESS_AUTH_TYPE`, and `INFOR_BUSINESS_ACCESS_TOKEN`; configure HubSpot using `HUBSPOT_BASE_URL` and `HUBSPOT_ACCESS_TOKEN`. Do not activate a non-local profile for live sync until the documented APIs, credentials, HubSpot custom property names, and required associations are verified.

The new business sync routes are individual synchronous operations. The existing Contact and Account batch queues remain as implemented; asynchronous batch queues for opportunities, orders, invoices, and products have not been added.

## Run locally

1. Start PostgreSQL:

```bash
docker compose up -d
```

2. Run the project:

```bash
mvn spring-boot:run
```

3. Submit a mock batch:

```bash
curl -X POST http://localhost:8081/api/sync/contacts/batch \
  -H "Content-Type: application/json" \
  -d "{\"contactIds\":[\"103\"]}"
```

## AWS SQS

For durable queues, run with a non-local Spring profile (for example, `SPRING_PROFILES_ACTIVE=prod`) and configure a separate SQS standard queue and dead-letter queue for contacts and accounts. The AWS SDK uses its default credential provider chain; grant the application role/user `sqs:SendMessage`, `sqs:ReceiveMessage`, and `sqs:DeleteMessage` on each main queue and `sqs:SendMessage` on each dead-letter queue. The INFOR side uses the configurable SData client; the `local` profile hosts its own SData mock. A live non-local setup still needs an SData URL and an authentication provider appropriate to that Infor environment. This starter currently has only a local mock HubSpot client; a real HubSpot client is still needed before running full sync outside `local`.

Configure these environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `AWS_REGION` | AWS region for the SQS client | `us-east-1` |
| `CONTACT_SYNC_QUEUE_URL` | Main contact-sync queue URL | Required outside `local` |
| `CONTACT_SYNC_DLQ_URL` | Dead-letter queue URL | Required outside `local` |
| `ACCOUNT_SYNC_QUEUE_URL` | Main account-sync queue URL | Required outside `local` |
| `ACCOUNT_SYNC_DLQ_URL` | Account-sync dead-letter queue URL | Required outside `local` |
| `CONTACT_SYNC_POLL_INTERVAL_MS` | Delay between worker polls | `1000` |
| `CONTACT_SYNC_MAX_ATTEMPTS` | Attempts before dead-lettering | `5` |

Set the main queue visibility timeout longer than the maximum expected processing time for one contact, with some margin. The producer publishes up to 10 messages per SQS batch call, and each worker poll receives up to 10 messages for bounded-concurrency processing. Increase throughput by running additional application instances. SQS standard queues deliver at least once, so consumers and downstream sync operations should remain idempotent.

## Important

This is a starter project, not production-ready code.

The following still need to be implemented/confirmed:

- Real INFOR CRM API contracts
- Real INFOR ERP/Data Lake contracts
- Real HubSpot API client
- Final HubSpot internal property name for ERP Contact Number
- Reverse HubSpot -> INFOR Contact flow
- Deal, Order, Invoice and Line Item workflows
- EventBridge integration
- Secrets Manager
- Audit/S3
- CloudWatch monitoring
- Authentication and security controls
- Full test coverage
