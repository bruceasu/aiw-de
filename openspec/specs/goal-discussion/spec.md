## Purpose

Define the console goal discussion workflow delivered by FD-019.

## Requirements

### Requirement: Durable actor-owned goals
The system SHALL persist each goal's content, conversation, selected sources and recoverable deletion state under its authenticated console actor. Without console authentication, clients share the existing local-console actor. Suggestions SHALL NOT overwrite human goal edits.

#### Scenario: A late discussion response
- **WHEN** a response refers to an older goal revision
- **THEN** it is shown as an older suggestion without editing the goal
- **AND** deletion followed by restoration permanently excludes responses from the earlier deletion epoch

### Requirement: Frozen discussion input and backend
The system SHALL dispatch goal discussion to the independent goal-discussion Worker using the selected backend and frozen approved AI profiles. It SHALL send only saved goal data, selected project knowledge and explicitly selected extracted attachment text. Missing matching Workers or profiles SHALL produce an actionable failure without switching providers.

#### Scenario: Attachment conversion cannot establish readable content
- **WHEN** conversion fails, extracts no text, or an image needs visual review
- **THEN** the original file remains available with an explicit status
- **AND** the system SHALL NOT claim that the Worker understood it

### Requirement: Formal snapshots and recoverable plan editing
The system SHALL create immutable formal goal snapshots. Missing information SHALL retain a not-ready marker and require explicit user acceptance before formalization. Plan editing snapshots MAY have incomplete or zero steps and SHALL remain recoverable independently of executable plan validation.

#### Scenario: AI Steps cannot complete the draft
- **WHEN** analysis is unavailable, fails or becomes stale
- **THEN** saved plan editing content remains available for manual completion
- **AND** no execution is started

### Requirement: Explicit execution preparation
The system SHALL require explicit handoff confirmation before saving an operator-visible execution plan. Missing coding workspaces SHALL be created under a managed root during execution preparation, initialized with Git when available and retained afterward. The saved WorkflowPlan SHALL satisfy existing validation and require a separate execution confirmation.

#### Scenario: Repeated preparation
- **WHEN** the same formal version is prepared again
- **THEN** the existing saved plan is returned rather than creating duplicate plans
