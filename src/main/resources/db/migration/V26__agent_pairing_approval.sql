-- A paired Agent remains inactive until one human action approves its exact Grant.
ALTER TABLE authority_identity ADD COLUMN approval_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY';
UPDATE authority_identity SET approval_state=CASE
    WHEN identity_kind='REVOKED_AGENT' THEN 'REVOKED'
    ELSE 'LEGACY'
END;
