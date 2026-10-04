-- US16: catálogo de materiales. SKU único por organización; tolerancia de merma entre 0 y 100 %.
CREATE TABLE organization.materials (
    id                      UUID PRIMARY KEY,
    organization_id         UUID         NOT NULL,
    sku                     VARCHAR(40)  NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    unit                    VARCHAR(10)  NOT NULL,
    waste_tolerance_percent NUMERIC(5, 2) NOT NULL,
    active                  BOOLEAN      NOT NULL,
    version                 BIGINT       NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              UUID         NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    updated_by              UUID         NOT NULL,
    CONSTRAINT uk_materials_organization_sku UNIQUE (organization_id, sku),
    CONSTRAINT ck_materials_waste_tolerance CHECK (waste_tolerance_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_materials_unit CHECK (unit IN ('UNIT', 'KG', 'TONNE', 'M', 'M2', 'M3', 'LITER', 'BAG', 'BOX', 'ROLL'))
);
