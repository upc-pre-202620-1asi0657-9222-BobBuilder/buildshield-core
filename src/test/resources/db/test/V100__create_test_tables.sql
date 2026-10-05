-- Tablas de entidades de prueba (solo para las pruebas de integración del kernel compartido).
CREATE TABLE test_notes (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    text            VARCHAR(200) NOT NULL
);

CREATE TABLE test_shipments (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    status          VARCHAR(30) NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID        NOT NULL
);
