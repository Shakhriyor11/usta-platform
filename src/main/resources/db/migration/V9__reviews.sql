-- =====================================================================
-- V9: Baho va sharhlar. 1 buyurtma = 1 baho. Reyting agregati master_profiles'da
-- (rating_sum — yaxlitlash xatosi to'planmasligi uchun aniq yig'indi).
-- =====================================================================
CREATE TABLE reviews (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL REFERENCES orders (id),
    customer_id UUID          NOT NULL REFERENCES users (id),
    master_id   UUID          NOT NULL REFERENCES master_profiles (user_id),
    rating      SMALLINT      NOT NULL,
    comment     VARCHAR(2000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_order  UNIQUE (order_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX ix_reviews_master ON reviews (master_id, created_at DESC);

ALTER TABLE master_profiles ADD COLUMN rating_sum BIGINT NOT NULL DEFAULT 0;
ALTER TABLE master_profiles ADD CONSTRAINT ck_master_rating_sum CHECK (rating_sum >= 0);
