-- =====================================================================
-- V15: Audit tuzatishlari.
--  1) commission_reports.on_hold — nizodagi buyurtma komissiyasi hisob-fakturaga va to'lovga kirmaydi.
--  2) order_master_offers.price_from — mijozga qidiruv paytida taxminiy narx oralig'ini ko'rsatish.
--  3) orders.dispatch_attempted_at — ustasi topilmayotgan buyurtmalar navbatda boshqalarni to'sib qo'ymasligi uchun.
--  4) chats: bitta buyurtmada har usta uchun alohida chat (usta voz kechib, boshqasi qabul qilishi mumkin).
-- =====================================================================
ALTER TABLE commission_reports ADD COLUMN on_hold BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE commission_reports c
   SET on_hold = TRUE
  FROM orders o
 WHERE o.id = c.order_id AND o.status = 'DISPUTED' AND c.status IN ('ACCRUED', 'INVOICED');

ALTER TABLE order_master_offers ADD COLUMN price_from NUMERIC(14, 2);

ALTER TABLE orders ADD COLUMN dispatch_attempted_at TIMESTAMPTZ;

ALTER TABLE chats DROP CONSTRAINT uq_chats_order;
ALTER TABLE chats ADD CONSTRAINT uq_chats_order_master UNIQUE (order_id, master_id);
