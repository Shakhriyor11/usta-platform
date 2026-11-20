-- =====================================================================
-- V5: Katalog — ierarxik kategoriyalar, xizmatlar, usta xizmatlari va narxlari, portfolio.
-- Nomlar ko'p tilli: *_uz majburiy, *_ru / *_en ixtiyoriy (bo'lmasa uz ko'rsatiladi).
-- =====================================================================
CREATE TABLE categories (
    id         UUID         PRIMARY KEY,
    parent_id  UUID         REFERENCES categories (id),
    slug       VARCHAR(80)  NOT NULL,
    name_uz    VARCHAR(120) NOT NULL,
    name_ru    VARCHAR(120),
    name_en    VARCHAR(120),
    icon       VARCHAR(255),
    sort_order INTEGER      NOT NULL DEFAULT 0,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_slug   UNIQUE (slug),
    CONSTRAINT ck_categories_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_categories_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_categories_parent ON categories (parent_id, sort_order);

CREATE TABLE services (
    id             UUID          PRIMARY KEY,
    category_id    UUID          NOT NULL REFERENCES categories (id),
    slug           VARCHAR(80)   NOT NULL,
    name_uz        VARCHAR(120)  NOT NULL,
    name_ru        VARCHAR(120),
    name_en        VARCHAR(120),
    description_uz VARCHAR(2000),
    description_ru VARCHAR(2000),
    description_en VARCHAR(2000),
    sort_order     INTEGER       NOT NULL DEFAULT 0,
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_services_slug   UNIQUE (slug),
    CONSTRAINT ck_services_slug   CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_services_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_services_category ON services (category_id, status, sort_order);

CREATE TABLE master_services (
    id          UUID           PRIMARY KEY,
    master_id   UUID           NOT NULL REFERENCES master_profiles (user_id) ON DELETE CASCADE,
    service_id  UUID           NOT NULL REFERENCES services (id),
    price_from  NUMERIC(14, 2) NOT NULL,
    description VARCHAR(1000),
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_master_services       UNIQUE (master_id, service_id),
    CONSTRAINT ck_master_services_price CHECK (price_from >= 0)
);

-- Qidiruv: xizmat bo'yicha faol ustalar va narx filtri
CREATE INDEX ix_master_services_service ON master_services (service_id, price_from) WHERE is_active;

CREATE TABLE master_portfolio (
    id           UUID         PRIMARY KEY,
    master_id    UUID         NOT NULL REFERENCES master_profiles (user_id) ON DELETE CASCADE,
    file_key     VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_master_portfolio_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_master_portfolio_master ON master_portfolio (master_id, created_at DESC);
