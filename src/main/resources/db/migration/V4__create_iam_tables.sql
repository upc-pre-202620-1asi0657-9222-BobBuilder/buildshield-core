-- Módulo iam: usuarios, sesiones y recuperación de contraseña.

CREATE TABLE iam.users (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    email           VARCHAR(254) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    role            VARCHAR(30)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    active          BOOLEAN      NOT NULL,
    version         BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID         NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      UUID         NOT NULL,
    -- El correo identifica al usuario al iniciar sesión: único entre todas las organizaciones.
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMINISTRATOR', 'WAREHOUSE_MANAGER', 'SITE_MANAGER'))
);

CREATE INDEX idx_users_organization ON iam.users (organization_id);

-- Tokens de renovación: solo se guarda el hash SHA-256 del valor entregado al cliente.
CREATE TABLE iam.refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES iam.users (id),
    organization_id UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_active_user ON iam.refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Enlaces de recuperación de contraseña: vencen y se usan una vez.
CREATE TABLE iam.password_reset_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES iam.users (id),
    organization_id UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

-- Tokens de acceso revocados por cierre de sesión, hasta que venzan.
CREATE TABLE iam.revoked_access_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_revoked_access_tokens_expires ON iam.revoked_access_tokens (expires_at);
