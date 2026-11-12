-- Repairs use the current failed page and exact installed collector, without an old successful page.
ALTER TABLE model_price_repair_material DROP COLUMN last_success_sha256;
ALTER TABLE model_price_repair_material DROP COLUMN last_success_sample_base64;
