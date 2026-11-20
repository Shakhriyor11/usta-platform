-- =====================================================================
-- V14: P3 da (payment jadvallaridan oldin) yakunlangan buyurtmalar uchun komissiya qatorlari, ledger va
-- to'lov yozuvlarini tiklash — aks holda reconciliation ularni nomuvofiq deb ko'rsatadi.
-- Idempotent: faqat amaldagi komissiyasi yo'q buyurtmalar.
-- =====================================================================
CREATE TEMP TABLE backfill_orders ON COMMIT DROP AS
SELECT o.id, o.master_id, o.price, o.commission_amount, o.completed_at
FROM orders o
WHERE o.commission_amount IS NOT NULL
  AND o.master_id IS NOT NULL
  AND o.price IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM commission_reports c WHERE c.order_id = o.id AND c.status <> 'CANCELLED');

-- UUIDv7 ga o'xshash tartib shart emas — tarixiy yozuvlar; gen_random_uuid() (PostgreSQL 13+)
INSERT INTO commission_reports (id, master_id, order_id, base_amount, rate_percent, amount, period, status, created_at)
SELECT gen_random_uuid(), b.master_id, b.id, b.price,
       CASE WHEN b.price > 0 THEN round(b.commission_amount * 100 / b.price, 2) ELSE 0 END,
       b.commission_amount,
       date_trunc('week', b.completed_at AT TIME ZONE 'Asia/Tashkent')::date,
       'ACCRUED', b.completed_at
FROM backfill_orders b;

INSERT INTO transactions (id, master_id, order_id, type, amount, reference, created_at)
SELECT gen_random_uuid(), b.master_id, b.id, 'COMMISSION_CHARGE', -b.commission_amount, 'order:' || b.id, b.completed_at
FROM backfill_orders b
WHERE b.commission_amount > 0;

INSERT INTO payments (id, order_id, master_id, amount, method, provider, status, paid_at, created_at)
SELECT gen_random_uuid(), b.id, b.master_id, b.price, 'CASH', 'cash', 'PAID', b.completed_at, b.completed_at
FROM backfill_orders b
WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = b.id);
