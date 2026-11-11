-- A repair proposal remains inert until a Plane owner approves its exact source and page.
ALTER TABLE model_price_repair_result ADD COLUMN source_url VARCHAR(2048);

-- Older proposals did not carry the official page URL and must be submitted again.
UPDATE model_price_repair_material
   SET status='SUPERSEDED'
 WHERE status='PREPARED' AND EXISTS (
       SELECT 1 FROM model_price_repair_result r
        WHERE r.material_id=model_price_repair_material.id AND r.source_url IS NULL);

ALTER TABLE model_price_repair_activation ADD COLUMN decision_by VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN decision_ms BIGINT;
ALTER TABLE model_price_repair_activation ADD COLUMN reviewed_candidate_sha256 VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN reviewed_page_sha256 VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN approved_by VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN approved_ms BIGINT;
ALTER TABLE model_price_repair_activation ADD COLUMN approved_candidate_sha256 VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN approved_page_sha256 VARCHAR(64);
ALTER TABLE model_price_repair_activation ADD COLUMN approved_page_base64 TEXT;
ALTER TABLE model_price_repair_activation ADD COLUMN approved_source_url VARCHAR(2048);
ALTER TABLE model_price_repair_activation ADD COLUMN approved_page_observed_ms BIGINT;
ALTER TABLE model_price_repair_activation ADD COLUMN execution_started_ms BIGINT;
ALTER TABLE model_price_repair_activation ADD COLUMN collection_lease_token VARCHAR(128);

-- Pre-approval activation leases cannot authorize any further candidate execution.
UPDATE model_price_repair_activation
   SET state='FAILED',lease_token=NULL,lease_until_ms=NULL,
       failure_code='ADMIN_REVIEW_REQUIRED',updated_ms=updated_ms
 WHERE approved_by IS NULL AND state IN ('VERIFYING','INSTALLING');
