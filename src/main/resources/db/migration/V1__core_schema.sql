-- Creates the message, outbox, and idempotency tables.

CREATE TABLE messages (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        NOT NULL,
    channel         text        NOT NULL CHECK (channel IN ('SMS', 'EMAIL')),
    recipient       text        NOT NULL,
    body            text        NOT NULL,
    status          text        NOT NULL DEFAULT 'ACCEPTED' CHECK (status IN ('ACCEPTED', 'SENT', 'FAILED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX messages_tenant_created ON messages (tenant_id, created_at DESC);

-- Written in the same transaction as the message row so the write and the
-- queue publish never disagree; a poller drains this table into SQS.
CREATE TABLE outbox_events (
    id             uuid        PRIMARY KEY,
    tenant_id      uuid        NOT NULL,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     text        NOT NULL,
    payload        jsonb       NOT NULL,
    attempts        int        NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz
);

-- Only unpublished rows are ever scanned by the poller.
CREATE INDEX outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    tenant_id       uuid        NOT NULL,
    key             text        NOT NULL,
    request_hash    text        NOT NULL,
    response_status int         NOT NULL,
    response_body   jsonb       NOT NULL,
    message_id      uuid        REFERENCES messages (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, key)
);
