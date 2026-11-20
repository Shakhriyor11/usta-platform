-- =====================================================================
-- V13: Shikoyatlar. Shikoyat nizo (orders.status = DISPUTED) ochishi mumkin; admin hal qiladi.
-- =====================================================================
CREATE TABLE complaints (
    id              UUID          PRIMARY KEY,
    order_id        UUID          NOT NULL REFERENCES orders (id),
    author_id       UUID          NOT NULL REFERENCES users (id),
    against_user_id UUID          NOT NULL REFERENCES users (id),
    reason          VARCHAR(16)   NOT NULL,
    description     VARCHAR(2000) NOT NULL,
    dispute         BOOLEAN       NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(2000),
    assigned_to     UUID,
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_complaints_reason CHECK (reason IN ('QUALITY', 'NO_SHOW', 'PRICE', 'BEHAVIOR', 'FRAUD', 'OTHER')),
    CONSTRAINT ck_complaints_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_complaints_parties CHECK (author_id <> against_user_id)
);

-- Bitta buyurtma bo'yicha bir muallifdan bitta ochiq shikoyat
CREATE UNIQUE INDEX uq_complaints_open ON complaints (order_id, author_id) WHERE status IN ('OPEN', 'IN_REVIEW');
CREATE INDEX ix_complaints_status  ON complaints (status, created_at);
CREATE INDEX ix_complaints_against ON complaints (against_user_id, created_at DESC);
CREATE INDEX ix_complaints_order   ON complaints (order_id);
