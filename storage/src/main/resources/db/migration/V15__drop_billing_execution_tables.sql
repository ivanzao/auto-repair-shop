-- Fase 4: tabelas de concerns que saíram do order.
-- - commands: o command bus (e-mail de orçamento) virou responsabilidade do billing.
-- - order_approval_tokens: a aprovação do orçamento é do billing (/v1/quotes/approve).
DROP TABLE IF EXISTS commands;
DROP TABLE IF EXISTS order_approval_tokens;
