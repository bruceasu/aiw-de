CREATE TABLE selected_goal_proposal (
 owner_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 projection_json TEXT NOT NULL, digest VARCHAR(64) NOT NULL, instance_id VARCHAR(128) NOT NULL,
 generation BIGINT NOT NULL, worker_boot VARCHAR(128) NOT NULL, platform_boot VARCHAR(128) NOT NULL,
 receipt_json TEXT NOT NULL, adopted_plan VARCHAR(96), adoption_digest VARCHAR(64), adoption_receipt TEXT,
 PRIMARY KEY(owner_id,request_id)
);
