# Persistence Model

## Minimal local-first relational model

A practical starting point for SQLite/PostgreSQL:

### artifact_current

- project_id
- artifact_id
- artifact_type
- version
- status
- title
- body_json
- updated_at
- PRIMARY KEY(project_id, artifact_id)

### artifact_revision

- project_id
- artifact_id
- version
- artifact_type
- body_json
- body_hash
- created_at
- created_by
- PRIMARY KEY(project_id, artifact_id, version)

### artifact_link

- project_id
- source_id
- source_version
- rel
- target_id
- target_version nullable

### baseline

- project_id
- baseline_id
- version
- baseline_type
- status
- manifest_json
- accepted_at nullable
- approval_id nullable

### baseline_item

- project_id
- baseline_id
- baseline_version
- artifact_id
- artifact_version
- content_hash nullable

### orchestration_event

- project_id
- event_id
- sequence_no
- subject_id
- event_type
- body_json
- created_at

### idempotency_record

- project_id
- request_id
- request_hash
- result_ref
- created_at

## Transaction pattern

For an accepted handoff batch:

1. begin transaction
2. lock/check expected current versions
3. validate references and authority
4. append artifact revisions
5. update current projections
6. write links/baseline candidate records
7. commit orchestration event(s)
8. store idempotency result
9. commit

Do not make the LLM responsible for transactionality.

## Hashing

A content hash is useful for baseline integrity. Canonicalize JSON deterministically before hashing if cross-language reproducibility is required. Specify one canonicalization algorithm rather than hashing pretty-printed JSON.
