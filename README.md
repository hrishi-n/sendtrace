# sendtrace

[![CI](https://github.com/hrishi-n/sendtrace/actions/workflows/ci.yml/badge.svg)](https://github.com/hrishi-n/sendtrace/actions/workflows/ci.yml)

A miniature Twilio/SendGrid-style message delivery backend: submit a message
over REST, get it durably queued for delivery via a transactional outbox, no
message ever silently lost between the database write and the queue publish.

Built with Spring Boot 3, Postgres 16, and plain SQL (no JPA). Multi-tenant,
idempotent, and backed by SQS (via LocalStack for local dev).

## Why this exists

The naive version of "submit a message" writes a row to the database, then
publishes an event to a queue. Those are two separate operations against two
separate systems, so a crash between them either loses the message or
double-processes it. The transactional outbox pattern fixes that by writing
the message and its outbox event in the *same* database transaction, then
having a separate poller drain the outbox into the queue - so the write and
the publish can never disagree.

- **Transactional outbox** - `POST /messages` writes the message row and its
  outbox event atomically. A scheduled poller claims unpublished rows with
  `FOR UPDATE SKIP LOCKED` (safe under concurrent pollers) and publishes them
  to SQS, marking each row published only after a successful send.
- **Idempotency** - retrying `POST /messages` with the same `Idempotency-Key`
  returns the original result, even if the retry races a duplicate insert.
- **Multi-tenant** - every table is tenant-scoped via `X-Tenant-Id`.
- **AWS-native queue** - real `SqsClient` calls, pointed at LocalStack
  locally and at real SQS in any deployed environment via one env var.

## Stack

Spring Boot 3.3.5, Java 21, Postgres 16 (plain JDBC, no ORM), Flyway, AWS SDK
v2 (SQS), LocalStack for local dev, Testcontainers for integration tests.

## Running locally

```
docker compose up --build
```

Brings up Postgres, LocalStack (with the `sendtrace-outbound` SQS queue
pre-created), and the app on `:8080`.

```
curl -X POST localhost:8080/messages \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"channel":"SMS","recipient":"+15551234567","body":"hello"}'
```

`GET /messages/{id}` reads it back. Swagger UI is at `/swagger-ui`.

## License

Apache License 2.0 - see [LICENSE](LICENSE).

## Running tests

Needs a Docker daemon (Testcontainers spins up a throwaway Postgres):

```
mvn test
```

## What's next

This is the foundation - REST intake plus the outbox. Not yet built:
async delivery workers that consume the SQS queue and update message status,
provider adapters (mock SMS/email) with Resilience4j circuit breakers and
failover, per-tenant rate limiting in Redis, signed delivery-status webhooks,
and Micrometer/Prometheus/Grafana observability (openledger has a working
example of that stack to copy from). Roadmap lives in `PROJECT_IDEAS.md`.
