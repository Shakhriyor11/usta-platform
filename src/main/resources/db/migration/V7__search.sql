-- =====================================================================
-- V7: Qidiruv read-modeli (CQRS). Search moduli profile/catalog jadvallariga to'g'ridan-to'g'ri
-- murojaat qilmaydi: bu jadvallar ularning outbox eventlari asosida yangilanadi. Bu yerda faqat
-- mijozlarga ko'rinadigan (APPROVED, faol, ish hududi va kamida bitta faol xizmati bor) ustalar.
-- =====================================================================
CREATE TABLE search_masters (
    master_id        UUID             PRIMARY KEY REFERENCES master_profiles (user_id) ON DELETE CASCADE,
    full_name        VARCHAR(120)     NOT NULL,
    avatar_key       VARCHAR(512),
    experience_years SMALLINT         NOT NULL,
    rating_avg       NUMERIC(3, 2)    NOT NULL,
    rating_count     INTEGER          NOT NULL,
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    location         geography(Point, 4326) GENERATED ALWAYS AS (
                         ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    work_radius_km   SMALLINT         NOT NULL,
    indexed_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX ix_search_masters_location ON search_masters USING GIST (location);
CREATE INDEX ix_search_masters_rating   ON search_masters (rating_avg DESC, rating_count DESC);

CREATE TABLE search_master_services (
    master_id  UUID           NOT NULL REFERENCES search_masters (master_id) ON DELETE CASCADE,
    service_id UUID           NOT NULL,
    price_from NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (master_id, service_id)
);

CREATE INDEX ix_search_master_services_service ON search_master_services (service_id, price_from);
