-- Yuklama testi ma'lumotlarini o'chirish (+99877...). Ledger (transactions) trigger bilan himoyalangan —
-- vaqtincha o'chiriladi; FAQAT test muhitida ishlating.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE lt_users AS SELECT id FROM users WHERE phone LIKE '+99877%';
CREATE TEMP TABLE lt_orders AS SELECT id FROM orders WHERE customer_id IN (SELECT id FROM lt_users)
                                                    OR master_id IN (SELECT id FROM lt_users);
ALTER TABLE transactions DISABLE TRIGGER tr_transactions_immutable;
DELETE FROM transactions WHERE master_id IN (SELECT id FROM lt_users) OR order_id IN (SELECT id FROM lt_orders);
ALTER TABLE transactions ENABLE TRIGGER tr_transactions_immutable;
DELETE FROM commission_reports WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM payments WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM complaints WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM reviews WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM lt_orders);
DELETE FROM outbox_events WHERE aggregate_id IN (SELECT id FROM lt_orders) OR aggregate_id IN (SELECT id FROM lt_users);
DELETE FROM search_masters WHERE master_id IN (SELECT id FROM lt_users);
DELETE FROM users WHERE id IN (SELECT id FROM lt_users);
COMMIT;
