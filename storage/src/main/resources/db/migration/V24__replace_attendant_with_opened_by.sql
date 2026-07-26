ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_attendant_id_fkey;

ALTER TABLE orders DROP COLUMN technician;

ALTER TABLE orders RENAME COLUMN attendant_id TO opened_by_id;

ALTER TABLE orders ADD COLUMN opened_by_document VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE orders ALTER COLUMN opened_by_document DROP DEFAULT;

ALTER TABLE orders ADD COLUMN diagnosed_by_id UUID;
ALTER TABLE orders ADD COLUMN diagnosed_by_document VARCHAR(20);

DROP TABLE IF EXISTS attendants;
