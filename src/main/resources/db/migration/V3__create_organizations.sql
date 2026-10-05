-- Módulo organization: la organización es el tenant; su id es el organization_id del resto de tablas.
CREATE TABLE organization.organizations (
    id         UUID PRIMARY KEY,
    ruc        VARCHAR(11)  NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_organizations_ruc UNIQUE (ruc),
    CONSTRAINT ck_organizations_ruc_digits CHECK (ruc ~ '^[0-9]{11}$')
);
