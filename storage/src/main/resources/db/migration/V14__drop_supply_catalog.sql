ALTER TABLE order_supplies DROP CONSTRAINT IF EXISTS order_supplies_supply_id_fkey;
ALTER TABLE service_supplies DROP CONSTRAINT IF EXISTS service_supplies_supply_id_fkey;

DROP TABLE IF EXISTS supplies;
