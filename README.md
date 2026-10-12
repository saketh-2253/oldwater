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
```

The mock follows the documented SData resource paths and JSON entry conventions, including `$key` references for related accounts and addresses. The middleware's existing `/api/infor/...` test endpoints now go through the SData client rather than accessing the mock store directly.

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
