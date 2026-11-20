-- =====================================================================
-- V4: Mijoz va usta profillari, manzillar, usta verifikatsiya hujjatlari.
-- Geo ustunlar (geography) latitude/longitude'dan GENERATED — ilova faqat lat/lng yozadi,
-- PostGIS so'rovlari (ST_DWithin, ST_Distance) GIST indeksli geography ustundan foydalanadi.
-- =====================================================================
CREATE TABLE customer_profiles (
    user_id    UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name  VARCHAR(120) NOT NULL,
    avatar_key VARCHAR(512),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE master_profiles (
    user_id             UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name           VARCHAR(120)  NOT NULL,
    bio                 VARCHAR(2000),
    experience_years    SMALLINT      NOT NULL DEFAULT 0,
    avatar_key          VARCHAR(512),
    work_latitude       DOUBLE PRECISION,
    work_longitude      DOUBLE PRECISION,
    work_radius_km      SMALLINT      NOT NULL DEFAULT 10,
    work_location       geography(Point, 4326) GENERATED ALWAYS AS (
                            CASE WHEN work_latitude IS NOT NULL AND work_longitude IS NOT NULL
                                 THEN ST_SetSRID(ST_MakePoint(work_longitude, work_latitude), 4326)::geography
                            END) STORED,
    verification_status VARCHAR(16)   NOT NULL DEFAULT 'NOT_SUBMITTED',
    is_verified         BOOLEAN       GENERATED ALWAYS AS (verification_status = 'APPROVED') STORED,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    rejection_reason    VARCHAR(1000),
    submitted_at        TIMESTAMPTZ,
    verified_at         TIMESTAMPTZ,
    verified_by         UUID,
    rating_avg          NUMERIC(3, 2) NOT NULL DEFAULT 0,
    rating_count        INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_master_experience  CHECK (experience_years BETWEEN 0 AND 70),
    CONSTRAINT ck_master_radius      CHECK (work_radius_km BETWEEN 1 AND 200),
    CONSTRAINT ck_master_lat         CHECK (work_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_master_lng         CHECK (work_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_master_latlng_pair CHECK ((work_latitude IS NULL) = (work_longitude IS NULL)),
    CONSTRAINT ck_master_verification CHECK (verification_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_master_rating_avg   CHECK (rating_avg BETWEEN 0 AND 5),
    CONSTRAINT ck_master_rating_count CHECK (rating_count >= 0)
);

CREATE INDEX ix_master_profiles_work_location ON master_profiles USING GIST (work_location);
CREATE INDEX ix_master_profiles_verification  ON master_profiles (verification_status, submitted_at);
-- Qidiruv uchun: faqat tasdiqlangan va faol ustalar, reyting bo'yicha tartiblangan
CREATE INDEX ix_master_profiles_searchable    ON master_profiles (rating_avg DESC, rating_count DESC)
    WHERE is_verified AND is_active;

CREATE TABLE master_verification_documents (
    id           UUID         PRIMARY KEY,
    master_id    UUID         NOT NULL REFERENCES master_profiles (user_id) ON DELETE CASCADE,
    doc_type     VARCHAR(16)  NOT NULL,
    file_key     VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mvd_type CHECK (doc_type IN ('PASSPORT', 'SELFIE', 'CERTIFICATE', 'OTHER')),
    CONSTRAINT ck_mvd_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_mvd_master ON master_verification_documents (master_id, created_at);

CREATE TABLE addresses (
    id         UUID             PRIMARY KEY,
    user_id    UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(60)      NOT NULL,
    address    VARCHAR(500)     NOT NULL,
    details    VARCHAR(255),
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    geo_point  geography(Point, 4326) GENERATED ALWAYS AS (
                   ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    is_default BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    version    BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_addresses_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_addresses_lng CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_addresses_user ON addresses (user_id, created_at);
CREATE INDEX ix_addresses_geo  ON addresses USING GIST (geo_point);
-- Har foydalanuvchida ko'pi bilan bitta asosiy manzil
CREATE UNIQUE INDEX uq_addresses_default ON addresses (user_id) WHERE is_default;
