-- Existing workers without a server-issued proof cannot receive model credentials.
ALTER TABLE model_auth_worker ADD COLUMN proof_digest VARCHAR(64);
ALTER TABLE model_auth_worker ADD COLUMN proof_cipher TEXT;
