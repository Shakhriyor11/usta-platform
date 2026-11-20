-- =====================================================================
-- V12: Buyurtma bo'yicha chat (mijoz <-> usta). Har buyurtmada bitta chat.
-- =====================================================================
CREATE TABLE chats (
    id                    UUID        PRIMARY KEY,
    order_id              UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    customer_id           UUID        NOT NULL REFERENCES users (id),
    master_id             UUID        NOT NULL REFERENCES users (id),
    customer_last_read_at TIMESTAMPTZ,
    master_last_read_at   TIMESTAMPTZ,
    last_message_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chats_order UNIQUE (order_id)
);

CREATE TABLE messages (
    id         UUID          PRIMARY KEY,
    chat_id    UUID          NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    sender_id  UUID          NOT NULL REFERENCES users (id),
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_messages_content CHECK (length(btrim(content)) > 0)
);

-- Tarixni sahifalash: (chat_id, created_at DESC, id DESC) bo'yicha keyset pagination
CREATE INDEX ix_messages_chat ON messages (chat_id, created_at DESC, id DESC);
