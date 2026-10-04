-- Tablas de buildshield-commons 0.1.1 (copiadas de db/buildshield-commons). Esquema public:
-- son transversales a los módulos (outbox, consumidores idempotentes, idempotencia HTTP).

CREATE TABLE idempotency_keys (
    organization_id       UUID         NOT NULL,
    idempotency_key       UUID         NOT NULL,
    request_method        VARCHAR(10)  NOT NULL,
    request_path          VARCHAR(500) NOT NULL,
    response_status       INT          NOT NULL,
    response_content_type VARCHAR(200),
    response_body         BYTEA,
    created_at            TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);

CREATE TABLE outbox (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(150)  NOT NULL,
    event_version   INT           NOT NULL,
    aggregate_id    VARCHAR(100)  NOT NULL,
    organization_id UUID          NOT NULL,
    correlation_id  VARCHAR(64)   NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    payload         JSONB         NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    sent_at         TIMESTAMPTZ,
    last_error      VARCHAR(1000)
);

CREATE INDEX idx_outbox_pending ON outbox (next_attempt_at, occurred_at) WHERE status = 'PENDING';

CREATE TABLE processed_messages (
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);

