-- =====================================================================
-- V2: Audit jurnali — admin va xavfsizlikka oid amallar.
-- =====================================================================
CREATE TABLE audit_logs (
    id         UUID         PRIMARY KEY,
    actor_id   UUID,
    action     VARCHAR(64)  NOT NULL,
    entity     VARCHAR(64)  NOT NULL,
    entity_id  UUID,
    details    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    trace_id   VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_entity ON audit_logs (entity, entity_id, created_at DESC);
CREATE INDEX ix_audit_logs_actor  ON audit_logs (actor_id, created_at DESC);
