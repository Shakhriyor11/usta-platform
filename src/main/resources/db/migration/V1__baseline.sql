-- =====================================================================
-- V1: Baseline — kengaytmalar va infratuzilma jadvallari.
-- Domen jadvallari keyingi migratsiyalarda modul bo'yicha qo'shiladi
-- (V2__identity, V3__profile, V4__catalog, ...).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------
-- Transactional outbox: domen eventlari biznes o'zgarish bilan bitta
-- tranzaksiyada yoziladi, OutboxRelay ularni keyin yetkazadi.
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    UUID,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT
);

-- Relay faqat yetkazilmagan xabarlarni o'qiydi — qisman indeks kichik va tez.
CREATE INDEX ix_outbox_events_pending   ON outbox_events (next_attempt_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
CREATE INDEX ix_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- ---------------------------------------------------------------------
-- ShedLock: klaster bo'ylab rejalashtirilgan vazifalarni bir martadan
-- bajarish uchun qulf jadvali.
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
