-- =====================================================================
-- V3: Identity & Access — foydalanuvchilar, rollar, refresh tokenlar.
-- =====================================================================
CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    phone          VARCHAR(13)  NOT NULL,
    password_hash  VARCHAR(255),                -- hozircha ishlatilmaydi (faqat OTP), kelajakda admin paroli uchun
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    blocked_reason VARCHAR(500),
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_phone  UNIQUE (phone),
    CONSTRAINT ck_users_phone  CHECK (phone ~ '^\+998[0-9]{9}$'),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE INDEX ix_users_status_created ON users (status, created_at DESC);

CREATE TABLE user_roles (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_roles      UNIQUE (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('CUSTOMER', 'MASTER', 'ADMIN'))
);

CREATE INDEX ix_user_roles_role ON user_roles (role);

-- Refresh token faqat SHA-256 hash ko'rinishida saqlanadi. Har yangilashda rotatsiya:
-- eski token revoked_at + replaced_by oladi; bir "family" ichida qayta ishlatish aniqlansa
-- butun family bekor qilinadi (o'g'irlangan token himoyasi).
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    ip          VARCHAR(64),
    user_agent  VARCHAR(255),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user    ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_family  ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
