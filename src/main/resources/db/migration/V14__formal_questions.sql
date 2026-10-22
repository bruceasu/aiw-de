CREATE TABLE formal_question (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, plan_id VARCHAR(128) NOT NULL,
 source_key VARCHAR(128) NOT NULL, source_digest VARCHAR(64) NOT NULL, request_digest VARCHAR(64) NOT NULL,
 payload_json TEXT NOT NULL, state VARCHAR(24) NOT NULL, request_version BIGINT NOT NULL,
 answer_revision BIGINT NOT NULL DEFAULT 0, answer_json TEXT, successor_task_id VARCHAR(128),
 blocked_reason VARCHAR(128), created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 UNIQUE(owner_id,plan_id,source_key)
);
CREATE TABLE formal_question_answer (
 question_id VARCHAR(128) NOT NULL, answer_revision BIGINT NOT NULL, answer_json TEXT NOT NULL,
 PRIMARY KEY(question_id,answer_revision)
);
CREATE TABLE formal_question_inbox (
 owner_id VARCHAR(128) NOT NULL, channel VARCHAR(32) NOT NULL, message_id VARCHAR(128) NOT NULL,
 question_id VARCHAR(128) NOT NULL, request_digest VARCHAR(64) NOT NULL, receipt_json TEXT NOT NULL,
 PRIMARY KEY(owner_id,channel,message_id)
);
CREATE TABLE formal_question_proposal (
 owner_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL,
 projection_json TEXT NOT NULL, projection_digest VARCHAR(64) NOT NULL,
 envelope_json TEXT NOT NULL, instance_id VARCHAR(128) NOT NULL, worker_generation BIGINT NOT NULL,
 worker_boot VARCHAR(128) NOT NULL, platform_boot VARCHAR(128) NOT NULL, PRIMARY KEY(owner_id,request_id)
);
CREATE TABLE formal_question_slot (
 source_task_id VARCHAR(128) PRIMARY KEY, question_id VARCHAR(128) NOT NULL UNIQUE,
 checkpoint_json TEXT NOT NULL, successor_task_id VARCHAR(128)
);
CREATE TABLE question_telegram_binding (
 owner_id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL, user_id VARCHAR(32) NOT NULL,
 chat_id VARCHAR(32) NOT NULL, generation BIGINT NOT NULL, enabled BOOLEAN NOT NULL,
 UNIQUE(user_id,chat_id)
);
CREATE TABLE question_telegram_outbox (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, question_id VARCHAR(128) NOT NULL,
 request_version BIGINT NOT NULL, binding_generation BIGINT NOT NULL, token_digest VARCHAR(64) NOT NULL,
 expires_ms BIGINT NOT NULL, state VARCHAR(24) NOT NULL, message_id VARCHAR(32),
 UNIQUE(token_digest)
);
CREATE TABLE question_telegram_inbox (
 update_id BIGINT PRIMARY KEY, identity_key VARCHAR(256) NOT NULL UNIQUE,
 received_ms BIGINT NOT NULL, outcome VARCHAR(32) NOT NULL
);
CREATE TABLE question_telegram_poll (
 bot_id VARCHAR(64) PRIMARY KEY, next_offset BIGINT NOT NULL
);
CREATE TABLE question_telegram_update (
 bot_id VARCHAR(64) NOT NULL, update_id BIGINT NOT NULL, payload_json TEXT NOT NULL,
 state VARCHAR(24) NOT NULL, PRIMARY KEY(bot_id,update_id)
);
