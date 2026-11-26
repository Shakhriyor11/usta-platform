-- =====================================================================
-- V16: Super admin va admin huquqlari, foydalanuvchini anonimlashtirish, katalogni o'chirish (arxivlash),
-- kategoriya/xizmat rasm va ikonkalari, o'zbek kirill nomlari, chatda rasm.
-- Diqqat: SUPER_ADMIN roli paydo bo'lgach undan oldingi image'ga qaytib bo'lmaydi (docs/operations.md).
-- =====================================================================

-- ---------------------------------------------------------------- rollar va huquqlar
ALTER TABLE user_roles DROP CONSTRAINT ck_user_roles_role;
ALTER TABLE user_roles ADD CONSTRAINT ck_user_roles_role
    CHECK (role IN ('CUSTOMER', 'MASTER', 'ADMIN', 'SUPER_ADMIN'));

CREATE TABLE user_permissions (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT ck_user_permissions CHECK (permission IN
        ('USERS', 'MASTERS', 'CATALOG', 'ORDERS', 'FINANCE', 'COMPLAINTS', 'SYSTEM', 'DELETE'))
);

-- Mavjud ADMIN yozuvlariga huquq berilmaydi: avval rol faqat ADMIN_PHONES dan kelardi — ro'yxatdagilar keyingi
-- kirishda SUPER_ADMIN bo'ladi, ro'yxatdan olib tashlangan sobiq adminning eski yozuvi esa hech narsaga kirmaydi.
-- Kerak bo'lsa super admin huquqlarni API orqali beradi.

ALTER TABLE users ADD COLUMN authz_version INTEGER NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------- foydalanuvchini o'chirish (anonimlashtirish)
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'));
-- telefon faqat o'chirilgan hisobda bo'sh: raqam bilan qayta ro'yxatdan o'tish mumkin
ALTER TABLE users ADD CONSTRAINT ck_users_phone_present CHECK ((status = 'DELETED') = (phone IS NULL));

-- ---------------------------------------------------------------- katalog
ALTER TABLE categories ADD COLUMN name_uz_cyrl VARCHAR(120);
ALTER TABLE categories ADD COLUMN image_key    VARCHAR(512);
ALTER TABLE categories ADD COLUMN icon_key     VARCHAR(512);
ALTER TABLE categories ADD COLUMN deleted_at   TIMESTAMPTZ;

ALTER TABLE services ADD COLUMN name_uz_cyrl        VARCHAR(120);
ALTER TABLE services ADD COLUMN description_uz_cyrl VARCHAR(2000);
ALTER TABLE services ADD COLUMN icon                VARCHAR(255);
ALTER TABLE services ADD COLUMN image_key           VARCHAR(512);
ALTER TABLE services ADD COLUMN icon_key            VARCHAR(512);
ALTER TABLE services ADD COLUMN deleted_at          TIMESTAMPTZ;

-- o'chirilgan element slug'i qayta ishlatilishi mumkin
ALTER TABLE categories DROP CONSTRAINT uq_categories_slug;
CREATE UNIQUE INDEX uq_categories_slug ON categories (slug) WHERE deleted_at IS NULL;
ALTER TABLE services DROP CONSTRAINT uq_services_slug;
CREATE UNIQUE INDEX uq_services_slug ON services (slug) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------- chatda rasm
ALTER TABLE messages ADD COLUMN image_key  VARCHAR(512);
ALTER TABLE messages ADD COLUMN image_type VARCHAR(100);
ALTER TABLE messages ADD COLUMN image_size BIGINT;
ALTER TABLE messages DROP CONSTRAINT ck_messages_content;
ALTER TABLE messages ADD CONSTRAINT ck_messages_content
    CHECK (length(btrim(content)) > 0 OR image_key IS NOT NULL);
CREATE INDEX ix_messages_sender_images ON messages (sender_id) WHERE image_key IS NOT NULL;
