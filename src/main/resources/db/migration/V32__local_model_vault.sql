-- A public installation/epoch reference and fingerprint, never the plaintext key.
CREATE TABLE model_vault_local (
 id INTEGER PRIMARY KEY, key_id VARCHAR(36) NOT NULL, key_digest VARCHAR(64)
);
