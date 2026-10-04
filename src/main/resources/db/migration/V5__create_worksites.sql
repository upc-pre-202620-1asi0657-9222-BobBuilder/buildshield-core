-- US14: obras de cada organización.
CREATE TABLE organization.worksites (
    id              UUID PRIMARY KEY,
    organization_id UUID             NOT NULL,
    name            VARCHAR(150)     NOT NULL,
    address         VARCHAR(200)     NOT NULL,
    district        VARCHAR(100)     NOT NULL,
    city            VARCHAR(100)     NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    start_date      DATE             NOT NULL,
    end_date        DATE,
    version         BIGINT           NOT NULL,
    created_at      TIMESTAMPTZ      NOT NULL,
    created_by      UUID             NOT NULL,
    updated_at      TIMESTAMPTZ      NOT NULL,
    updated_by      UUID             NOT NULL,
    CONSTRAINT ck_worksites_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_worksites_organization ON organization.worksites (organization_id);
