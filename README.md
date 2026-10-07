# Modern Ice Integration Starter

Starter Spring Boot project for the INFOR CRM / INFOR ERP / HubSpot integration.

## Included

- Canonical models for Contact, Company, Deal, Order, Invoice, and LineItem
- INFOR Contact DTO + mapper
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

For a durable queue, run with a non-local Spring profile (for example, `SPRING_PROFILES_ACTIVE=prod`) and configure an SQS standard queue plus a separate standard dead-letter queue. The AWS SDK uses its default credential provider chain; grant the application role/user `sqs:SendMessage`, `sqs:ReceiveMessage`, and `sqs:DeleteMessage` on the contact queue and `sqs:SendMessage` on the dead-letter queue. This starter currently provides only local mock CRM/HubSpot clients; real client implementations must be configured before the non-local profile can start successfully.

Configure these environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `AWS_REGION` | AWS region for the SQS client | `us-east-1` |
| `CONTACT_SYNC_QUEUE_URL` | Main contact-sync queue URL | Required outside `local` |
| `CONTACT_SYNC_DLQ_URL` | Dead-letter queue URL | Required outside `local` |
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
- Company, Deal, Order, Invoice and Line Item workflows
- EventBridge integration
- Secrets Manager
- Audit/S3
- CloudWatch monitoring
- Authentication and security controls
- Full test coverage
