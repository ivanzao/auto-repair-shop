-- Fase 4: o catálogo/estoque de insumos (supplies) migra para o execution service.
-- O order guarda apenas as referências (supply_id, quantity) em order_supplies e
-- service_supplies, sem FK para um catálogo local (o catálogo passa a viver no execution).
ALTER TABLE order_supplies DROP CONSTRAINT IF EXISTS order_supplies_supply_id_fkey;
ALTER TABLE service_supplies DROP CONSTRAINT IF EXISTS service_supplies_supply_id_fkey;

DROP TABLE IF EXISTS supplies;
