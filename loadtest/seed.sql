-- =====================================================================
-- Yuklama testi ma'lumotlari: 2000 mijoz (manzili bilan) va 500 tasdiqlangan usta (Toshkent bo'ylab).
-- Barchasi +99877XXXXXXX raqamlarda — cleanup.sql bilan o'chiriladi. Idempotent emas: avval cleanup.
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE lt_customers AS
SELECT md5('lt-customer-' || i)::uuid AS id, '+99877' || lpad(i::text, 7, '0') AS phone,
       41.24 + random() * 0.14 AS lat, 69.16 + random() * 0.20 AS lng
FROM generate_series(1, 2000) i;

CREATE TEMP TABLE lt_masters AS
SELECT md5('lt-master-' || i)::uuid AS id, '+99877' || lpad((5000000 + i)::text, 7, '0') AS phone,
       41.24 + random() * 0.14 AS lat, 69.16 + random() * 0.20 AS lng,
       (8 + floor(random() * 12))::int AS radius,
       round((3.5 + random() * 1.5)::numeric, 2) AS rating, (floor(random() * 200))::int AS cnt
FROM generate_series(1, 500) i;

INSERT INTO users (id, phone, status) SELECT id, phone, 'ACTIVE' FROM lt_customers
UNION ALL SELECT id, phone, 'ACTIVE' FROM lt_masters;
INSERT INTO user_roles (user_id, role) SELECT id, 'CUSTOMER' FROM lt_customers;
INSERT INTO user_roles (user_id, role) SELECT id, 'MASTER' FROM lt_masters;

INSERT INTO customer_profiles (user_id, full_name) SELECT id, 'Yuklama mijoz ' || right(phone, 4) FROM lt_customers;
INSERT INTO addresses (id, user_id, title, address, latitude, longitude, is_default)
SELECT md5('lt-address-' || id)::uuid, id, 'Uy', 'Toshkent, yuklama ko''chasi', lat, lng, true FROM lt_customers;

INSERT INTO master_profiles (user_id, full_name, experience_years, work_latitude, work_longitude, work_radius_km,
                             verification_status, verified_at, rating_avg, rating_count, rating_sum)
SELECT id, 'Yuklama usta ' || right(phone, 4), 5, lat, lng, radius, 'APPROVED', now(), rating, cnt,
       round(rating * cnt)
FROM lt_masters;

-- har ustada santexnika xizmatlaridan 2-3 tasi
INSERT INTO master_services (id, master_id, service_id, price_from, is_active)
SELECT gen_random_uuid(), m.id, s.id, (50 + floor(random() * 250)) * 1000, true
FROM lt_masters m
JOIN services s ON s.slug IN ('faucet-install', 'pipe-leak', 'toilet-install')
WHERE random() < 0.85 OR s.slug = 'faucet-install';

COMMIT;

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SELECT (SELECT count(*) FROM lt_customers) AS customers, (SELECT count(*) FROM lt_masters) AS masters;
