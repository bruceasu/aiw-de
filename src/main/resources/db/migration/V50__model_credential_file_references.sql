-- Token protocol/storage moves to Python. Retain metadata/history, require explicit reconnection.
ALTER TABLE model_connection RENAME COLUMN secret_cipher TO credential_file;
UPDATE model_connection SET credential_file=NULL,state='NEEDS_LOGIN',version=version+1;
