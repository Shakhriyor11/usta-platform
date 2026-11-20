-- =====================================================================
-- V11: To'lovlar, komissiya hisobotlari va ledger.
-- Model (naqd to'lov): mijoz ustaga naqd to'laydi -> usta platformaga komissiya qarzdor bo'ladi.
-- Ledger (transactions) — ustaning platforma oldidagi hisobi: manfiy = qarz, musbat = ortiqcha to'lov.
-- Yozuvlar o'zgarmas (UPDATE/DELETE trigger bilan taqiqlangan) — tuzatish faqat teskari yozuv bilan.
-- =====================================================================
CREATE TABLE payments (
    id           UUID           PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES orders (id),
    master_id    UUID           NOT NULL REFERENCES master_profiles (user_id),
    amount       NUMERIC(14, 2) NOT NULL,
    method       VARCHAR(16)    NOT NULL,
    provider     VARCHAR(32)    NOT NULL,
    provider_ref VARCHAR(128),
    status       VARCHAR(16)    NOT NULL,
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_order  UNIQUE (order_id),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'REFUNDED', 'FAILED'))
);

CREATE INDEX ix_payments_master ON payments (master_id, created_at DESC);

-- Har yakunlangan buyurtma uchun komissiya qatori; period — hafta boshi (dushanba, Asia/Tashkent).
CREATE TABLE commission_reports (
    id           UUID           PRIMARY KEY,
    master_id    UUID           NOT NULL REFERENCES master_profiles (user_id),
    order_id     UUID           NOT NULL REFERENCES orders (id),
    base_amount  NUMERIC(14, 2) NOT NULL,
    rate_percent NUMERIC(5, 2)  NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    period       DATE           NOT NULL,
    status       VARCHAR(16)    NOT NULL DEFAULT 'ACCRUED',
    invoiced_at  TIMESTAMPTZ,
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_commission_amount CHECK (amount >= 0 AND base_amount >= 0),
    CONSTRAINT ck_commission_rate   CHECK (rate_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_commission_period CHECK (EXTRACT(ISODOW FROM period) = 1),
    CONSTRAINT ck_commission_status CHECK (status IN ('ACCRUED', 'INVOICED', 'PAID', 'CANCELLED'))
);

-- Bitta buyurtmada faqat bitta amaldagi komissiya (qayta hisoblashda eskisi CANCELLED bo'ladi)
CREATE UNIQUE INDEX uq_commission_active_order ON commission_reports (order_id) WHERE status <> 'CANCELLED';
CREATE INDEX ix_commission_master_period ON commission_reports (master_id, period, status);
CREATE INDEX ix_commission_period_status ON commission_reports (period, status);

CREATE TABLE transactions (
    id         UUID           PRIMARY KEY,
    master_id  UUID           NOT NULL REFERENCES master_profiles (user_id),
    order_id   UUID           REFERENCES orders (id),
    type       VARCHAR(24)    NOT NULL,
    amount     NUMERIC(14, 2) NOT NULL,
    reference  VARCHAR(128),
    created_by UUID,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_type   CHECK (type IN ('COMMISSION_CHARGE', 'COMMISSION_REVERSAL', 'COMMISSION_PAYMENT', 'ADJUSTMENT')),
    CONSTRAINT ck_transactions_amount CHECK (amount <> 0),
    CONSTRAINT ck_transactions_sign   CHECK (
        (type = 'COMMISSION_CHARGE' AND amount < 0) OR
        (type IN ('COMMISSION_REVERSAL', 'COMMISSION_PAYMENT') AND amount > 0) OR
        type = 'ADJUSTMENT')
);

CREATE INDEX ix_transactions_master ON transactions (master_id, created_at DESC);
CREATE INDEX ix_transactions_order  ON transactions (order_id) WHERE order_id IS NOT NULL;

CREATE FUNCTION transactions_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'transactions (ledger) is append-only: % is not allowed', TG_OP;
END $$;

CREATE TRIGGER tr_transactions_immutable BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION transactions_immutable();
