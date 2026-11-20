-- =====================================================================
-- V8: Buyurtmalar, ustalarga takliflar, status tarixi, biriktirmalar.
-- Manzil buyurtma yaratilgan paytdagi holatida saqlanadi (snapshot) — mijoz keyin manzilini
-- o'zgartirsa ham buyurtma o'zgarmaydi.
-- =====================================================================
CREATE TABLE orders (
    id                  UUID             PRIMARY KEY,
    customer_id         UUID             NOT NULL REFERENCES users (id),
    master_id           UUID             REFERENCES master_profiles (user_id),
    preferred_master_id UUID             REFERENCES master_profiles (user_id),
    service_id          UUID             NOT NULL REFERENCES services (id),
    status              VARCHAR(16)      NOT NULL,
    description         VARCHAR(2000)    NOT NULL,
    -- manzil snapshot'i
    address_title       VARCHAR(60),
    address_text        VARCHAR(500)     NOT NULL,
    address_details     VARCHAR(255),
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    location            geography(Point, 4326) GENERATED ALWAYS AS (
                            ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    scheduled_at        TIMESTAMPTZ,
    search_deadline     TIMESTAMPTZ      NOT NULL,
    -- narx: price_from — qabul qilgan ustaning "narx dan" qiymati, price — usta yakunda kiritgan yakuniy narx
    price_from          NUMERIC(14, 2),
    price               NUMERIC(14, 2),
    commission_amount   NUMERIC(14, 2),
    accepted_at         TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    cancel_reason       VARCHAR(500),
    cancelled_by        UUID,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    version             BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_status CHECK (status IN ('CREATED', 'SEARCHING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED',
                                                  'COMMISSION', 'REVIEWED', 'CANCELLED', 'EXPIRED', 'DISPUTED')),
    CONSTRAINT ck_orders_lat   CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_orders_lng   CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_orders_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_orders_commission CHECK (commission_amount IS NULL OR commission_amount >= 0),
    -- usta biriktirilgan holatlarda master_id majburiy
    CONSTRAINT ck_orders_master_assigned CHECK (
        status IN ('CREATED', 'SEARCHING', 'CANCELLED', 'EXPIRED') OR master_id IS NOT NULL)
);

CREATE INDEX ix_orders_customer  ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_master    ON orders (master_id, created_at DESC) WHERE master_id IS NOT NULL;
CREATE INDEX ix_orders_status    ON orders (status, created_at DESC);
CREATE INDEX ix_orders_searching ON orders (search_deadline) WHERE status = 'SEARCHING';
CREATE INDEX ix_orders_location  ON orders USING GIST (location);

CREATE TABLE order_master_offers (
    id           UUID        PRIMARY KEY,
    order_id     UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    master_id    UUID        NOT NULL REFERENCES master_profiles (user_id),
    offer_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    distance_m   INTEGER     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_offers_order_master UNIQUE (order_id, master_id),
    CONSTRAINT ck_offers_status CHECK (offer_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_offers_distance CHECK (distance_m >= 0)
);

-- Bitta buyurtmada faqat bitta qabul qilingan taklif bo'lishi mumkin (lock'dan tashqari himoya qatlami)
CREATE UNIQUE INDEX uq_offers_accepted ON order_master_offers (order_id) WHERE offer_status = 'ACCEPTED';
CREATE INDEX ix_offers_master_pending  ON order_master_offers (master_id, created_at DESC) WHERE offer_status = 'PENDING';
CREATE INDEX ix_offers_pending_expiry  ON order_master_offers (expires_at) WHERE offer_status = 'PENDING';

CREATE TABLE order_status_history (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    old_status VARCHAR(16),
    new_status VARCHAR(16) NOT NULL,
    changed_by UUID,                 -- NULL — tizim (muddat tugashi va h.k.)
    reason     VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, changed_at);

CREATE TABLE order_attachments (
    id           UUID         PRIMARY KEY,
    order_id     UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    file_key     VARCHAR(512) NOT NULL,
    media_type   VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_attachments_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_order_attachments_order ON order_attachments (order_id, created_at);
