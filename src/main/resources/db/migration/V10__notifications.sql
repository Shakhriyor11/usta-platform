-- =====================================================================
-- V10: In-app bildirishnomalar va push qurilma tokenlari.
-- Matn type + args bo'yicha o'qish paytida foydalanuvchi tilida hosil qilinadi.
-- =====================================================================
CREATE TABLE notifications (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(40)  NOT NULL,
    title           VARCHAR(200) NOT NULL,    -- standart tilda (uz), arxiv/qidiruv uchun
    body            VARCHAR(1000) NOT NULL,
    args            JSONB        NOT NULL DEFAULT '[]',
    data            JSONB        NOT NULL DEFAULT '{}',
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    source_event_id UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- outbox at-least-once: bitta event bir foydalanuvchiga bir marta
    CONSTRAINT uq_notifications_event_user UNIQUE (source_event_id, user_id)
);

CREATE INDEX ix_notifications_user   ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE NOT is_read;

CREATE TABLE push_tokens (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(16)  NOT NULL,
    locale       VARCHAR(8)   NOT NULL DEFAULT 'uz',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_tokens_token UNIQUE (token),
    CONSTRAINT ck_push_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX ix_push_tokens_user ON push_tokens (user_id);
