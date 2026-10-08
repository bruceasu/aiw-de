# AI Dev Manager — Web Control Plane Handoff

## 1. Current Objective

Continue the AI Dev Manager project from the accepted R9 baseline.

The immediate priority is now:

> **Improve and complete the Web Control Plane first.**

Do not continue investing significant effort in the Dear ImGui desktop companion until the Web Control Plane reaches a usable and coherent state.

The desktop Worker / Watchdog UI remains a valid future direction, but it is not the current critical path.

---

# 2. Frozen Baseline

The accepted baseline is:

## R9 Web Console Fix1

Confirmed by the user with:

```bash
mvn clean verify
```

and all tests passed.

Current baseline:

```text
Java package root     me.asu.aidev
Java                  21
Spring Boot           4
Spring Framework      7
Build                 Maven

Architecture          Worker Pull
Control Plane         Java
Web Console           included
Persistence           Flyway
Flyway version        V14
Persisted tables      9
Integration tests     33 Failsafe IT methods passing
```

R9 Web Console Fix1 must be treated as the frozen starting point.

Do not regress previously accepted capabilities.

Do not reintroduce old centralized orchestration patterns.

---

# 3. Architectural Direction

The system should remain conceptually separated into:

```text
                    AI Dev Manager
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
  Control Plane                     Worker Runtime
  Java / Spring Boot                Pull-based
  Web UI                            decentralized
         │                               │
         │                               ├── Java
         │                               ├── Go
         │                               ├── Python
         │                               ├── TypeScript
         │                               └── other workers
         │
         ▼
 Browser Web Console
```

The Control Plane manages state, authority, scheduling, visibility and operator interaction.

Workers execute work independently using the Worker Pull model.

The Control Plane must not become a centralized process runner.

---

# 4. UI Direction

The main user interface should be:

> **Browser-based Web Control Plane**

Not a desktop application.

The user prefers the Web UI to eventually have a modern:

```text
future
space
mission-control
game-HUD inspired
```

visual identity.

However, usability and information clarity take priority over decorative effects.

A useful balance is approximately:

```text
70% professional engineering console
20% science-fiction / mission-control identity
10% game-like ambient feedback
```

Do not turn the Web Console into a visually noisy cyberpunk demo.

It is a production engineering system.

---

# 5. Preferred Web Technology Direction

The user strongly prefers keeping the architecture simple.

Avoid introducing a heavy frontend stack unless there is a demonstrated requirement.

Preferred starting direction:

```text
Spring Boot 4
Spring MVC
Thymeleaf
HTMX
CSS
small amounts of native JavaScript
```

For realtime updates:

```text
SSE
or
WebSocket
```

Prefer SSE where communication is primarily server → browser.

Use WebSocket only where true bidirectional realtime communication is useful.

For visualizations:

```text
CSS
SVG
Canvas
```

Only introduce WebGL / Three.js if a particular visualization genuinely benefits from it.

Do not introduce React, Vue, Next.js or a Node frontend build chain merely to make the UI look modern.

The system should remain easy for a small Java-focused team to build, debug and deploy.

---

# 6. Main Web Control Plane Concept

The Control Plane should evolve toward an:

> **AI Engineering Mission Control**

The purpose is not only CRUD administration.

The operator should be able to understand:

```text
What is the system doing?
Why is it doing it?
Who is doing it?
What is blocked?
What needs human attention?
What has failed?
What will happen next?
```

within a few seconds.

The Web Console should therefore emphasize operational state and execution flow.

---

# 7. Proposed Primary Navigation

A tentative information architecture is:

```text
Overview

Missions
    Requirements
    Runs
    Plans
    Work Items

Fleet
    Workers
    Capacity
    Backlog
    Scheduling

Engineering
    Artifacts
    Integration
    Review
    Verification
    Repair

Approvals
    Pending
    History
    Policies

Observability
    Timeline
    Events
    Failures
    Recovery

Administration
    Runtime Control
    Scheduling Policy
    Concurrency
    Configuration
```

Do not treat this exact menu as frozen.

It should be validated against the actual R9 domain model before implementation.

---

# 8. Overview / Mission Control Dashboard

The homepage should become the primary operational view.

A possible structure:

```text
┌───────────────────────────────────────────────────────────┐
│ AIW MISSION CONTROL                         CORE ONLINE   │
├────────────────┬──────────────────────────────────────────┤
│                │                                          │
│ ACTIVE         │           ACTIVE MISSIONS                │
│ MISSIONS       │                                          │
│                │    R10 Worker Migration                  │
│  4 Running     │    ███████████████░░░  78%              │
│  1 Blocked     │                                          │
│  2 Waiting     │    R11 Integration                       │
│                │    ███████░░░░░░░░░░  36%              │
│                │                                          │
├────────────────┼──────────────────────────────────────────┤
│ FLEET          │           EXECUTION FLOW                 │
│                │                                          │
│ Workers 12     │ Planner → Coder → Review                 │
│ Busy     7     │                    ↓                     │
│ Idle     4     │                Verification              │
│ Error    1     │                    ↓                     │
│                │               Integration                │
├────────────────┼──────────────────────────────────────────┤
│ QUEUE          │              ATTENTION                   │
│                │                                          │
│ Pending  37    │ ⚠ 1 worker unhealthy                    │
│ Review    5    │ ! 2 approvals required                  │
│ Verify    3    │ × 1 verification failed                 │
└────────────────┴──────────────────────────────────────────┘
```

The dashboard should answer:

```text
system health
active work
queue pressure
worker availability
human attention
recent failures
current execution stage
```

without forcing the operator to inspect multiple pages.

---

# 9. Mission Model in the UI

The UI may use the term:

```text
Mission
```

as a presentation concept for a higher-level requirement/run.

However, internal domain terminology should remain technically correct.

For example:

```text
Mission
    Requirement
    Run
    Plan
    Work Items
    Artifacts
    Review
    Verification
    Approval
```

Avoid renaming the persistence/domain model purely to achieve a game-like appearance.

The visual metaphor must sit above the engineering semantics, not replace them.

---

# 10. Mission Detail View

A Mission detail page should ideally contain:

```text
Mission Header

Status
Requirement
Run ID
Created
Started
Elapsed
Priority
Owner/operator if applicable
```

Then:

```text
Execution graph
```

For example:

```text
Requirement
     │
     ▼
Planning
     │
 ┌───┴──────────┐
 ▼              ▼
Implementation A
Implementation B
 │              │
 ▼              ▼
Review       Verification
 └──────┬───────┘
        ▼
Integration
        │
        ▼
Engineering Gate
        │
        ▼
Human / Auto Approval
        │
        ▼
Completed
```

The graph should represent actual execution state, not a hard-coded static process diagram.

Possible implementation:

```text
SVG
```

is preferred initially.

Do not introduce a heavy graph library unless needed.

---

# 11. Worker Fleet View

Fleet should expose both:

```text
logical workers
```

and:

```text
runtime capacity
```

Important information:

```text
worker ID
worker type / role
runtime
node
status
leased work
current run
current work item
last heartbeat
lease expiry
capabilities
health
failure state
```

Potential statuses:

```text
ONLINE
IDLE
CLAIMING
BUSY
WAITING
DEGRADED
UNHEALTHY
OFFLINE
```

Do not assume these exact names without checking the existing R9 model.

Reuse existing domain states where possible.

---

# 12. Scheduling and Capacity

The existing R8/R9 work already introduced:

```text
Scheduling policy
Fairness
Priority
Run concurrency budget
Admission control
Fleet admission budget
Runtime scheduling control
```

The Web Control Plane should expose these capabilities clearly.

The operator should be able to understand:

```text
Why was this run admitted?
Why is this run waiting?
Which budget is exhausted?
Which worker capacity is available?
Why did a worker receive this work item?
```

Do not build UI that only displays raw configuration values.

Expose the scheduling decision and reason where possible.

---

# 13. Timeline

The existing Run Timeline / Observability work should become a first-class UI concept.

Example:

```text
10:02  Requirement accepted
10:02  Run created
10:03  Plan generated
10:03  4 implementation items materialized
10:04  coder-01 leased item #31
10:04  coder-02 leased item #32
10:11  item #31 completed
10:12  review started
10:13  verification failed
10:14  repair item created
10:19  repair completed
10:21  engineering gate passed
10:21  waiting for approval
```

Operators should not need database access to reconstruct a run.

Timeline is one of the most important features of the Control Plane.

---

# 14. Human Attention

The system should make human intervention very obvious.

Examples:

```text
Approval required
Worker unhealthy
Run paused
Run blocked
Verification failed
Repair loop exceeded limit
Integration conflict
Capacity exhausted
Runtime unavailable
```

Consider a dedicated:

```text
ATTENTION
```

area on the dashboard.

This should be more important than generic notifications.

---

# 15. Approval UI

The Web Console should remain the authoritative rich approval interface.

Telegram remains useful as an external approval adapter, but Web should allow more context.

An approval page may show:

```text
Mission
Requirement
Current stage
Reason approval is required
Artifacts
Review result
Verification result
Risk / warnings
Recommended actions or available actions
```

Then actions such as:

```text
Approve
Reject
Request Repair
Pause
Cancel
```

Only expose actions that already exist or are consistent with the current authority model.

---

# 16. Artifacts

Engineering artifacts should become navigable from the Web UI.

Useful categories may include:

```text
Requirement
Architecture
Plan
Implementation result
Code review
Verification result
Repair result
Integration result
Final engineering gate
Approval record
```

The UI should make artifact provenance clear:

```text
who produced it
which work item produced it
when
which version
which run
```

Do not make artifacts anonymous blobs.

---

# 17. Failure and Recovery

R8-S4 introduced runtime status, failure and recovery authority.

The Web UI should make failures diagnosable.

For a failed run or worker, show:

```text
What failed?
When?
Which component?
Which work item?
Which worker?
Was retry attempted?
Was repair created?
Is recovery automatic?
Does human action remain?
```

Avoid only showing:

```text
FAILED
```

without operational explanation.

---

# 18. Visual Design System

Create a reusable theme rather than styling pages independently.

Working name:

```text
AIW Nebula
```

Possible visual rules:

```text
Background
    very dark navy / near-black

Panels
    dark layered surfaces

Primary accent
    cyan / electric blue

Secondary accent
    restrained violet

Success
    teal / green

Warning
    amber

Failure
    restrained red
```

Use:

```text
thin borders
subtle glow
small corner radius or clipped-corner motifs
monospaced text selectively
technical typography
subtle animated status indicators
```

Avoid:

```text
large neon glows everywhere
continuous distracting animation
low-contrast text
excessive transparency
fake sci-fi decorations that convey no information
```

The system will be used for extended engineering sessions.

Readability must remain high.

---

# 19. Animation Principles

Animations should communicate state.

Good examples:

```text
active worker        subtle pulse
task dispatched      short moving marker
waiting approval     slow attention pulse
failure              brief alert emphasis
connection lost      faded/disconnected line
queue pressure       metric transition
```

Bad examples:

```text
continuous random flashing
large animated backgrounds
constant particle effects over content
everything glowing
```

Ambient animation should remain optional and restrained.

---

# 20. Realtime Transport

Do not automatically use WebSocket everywhere.

Suggested decision:

## SSE

Prefer for:

```text
run status
worker heartbeat/status
timeline events
queue changes
alerts
metrics
```

because most of this is:

```text
server → browser
```

## REST

Use for operator commands:

```text
pause
resume
cancel
approve
reject
configuration changes
```

This combination may be simpler than maintaining a fully bidirectional WebSocket protocol.

Use WebSocket only if later functionality genuinely requires it.

---

# 21. Avoid Frontend Complexity

The user explicitly values:

```text
minimal operations
local-first development
simple deployment
few moving parts
```

Therefore avoid prematurely introducing:

```text
Node.js build
npm dependency tree
React
Vue
Next.js
Webpack/Vite configuration
separate frontend repository
separate frontend deployment pipeline
```

unless the benefits clearly outweigh the cost.

Prefer:

```text
one Maven project
one Spring Boot application
one deployable artifact
```

for the Control Plane where practical.

---

# 22. Desktop Companion — Deferred but Retained

A separate design discussion established a future native desktop component.

Working concept:

```text
AIW Companion
```

Potential implementation:

```text
C++
Dear ImGui
SDL3
OpenGL 3
```

Potential responsibilities:

```text
local worker status
watchdog status
system tray
small HUD
notifications
desktop character / desktop pet
```

Potential visualization:

```text
Worker        robot / character
Watchdog      small robotic dog
```

Worker states could drive character animations:

```text
IDLE
CODING
REVIEWING
VERIFYING
WAIT_APPROVAL
FAILED
COMPLETED
```

This concept is promising but currently deferred.

Do not allow it to distract from completing the Web Control Plane.

---

# 23. Important Architectural Rule for Companion

When this work resumes:

> Companion must remain an observer/controller, not an orchestration authority.

It should not become a new central scheduler.

Preferred conceptual relationship:

```text
Control Plane
     │
     ├──────────── Worker Runtime
     │
     └──────────── AIW Companion
```

The actual Worker Pull architecture remains unchanged.

---

# 24. Existing Companion PoCs

Two exploratory native PoCs were created during the previous session:

```text
AIW Mission Control Nebula P0
AIW Companion P1
```

P1 explored:

```text
transparent desktop window
Dear ImGui
SDL3
OpenGL
worker robot
watchdog dog
simulated worker states
HUD
```

These are experimental references only.

They are not part of the frozen R9 baseline.

Do not merge them into the Control Plane.

---

# 25. Immediate Next Step

Before writing significant new UI code:

## Step 1 — Inspect R9 Web Console

Identify:

```text
existing pages
controllers
templates
REST endpoints
current navigation
current CSS
current JavaScript
existing dashboard
run pages
worker/fleet pages
approval pages
timeline pages
runtime-control pages
```

Also map the existing pages back to the R9 domain model.

---

# 26. Step 2 — Produce a Web Console Gap Analysis

Create a concise gap matrix:

```text
Capability
Current backend support
Current UI support
Missing UI
Required backend changes
Priority
```

Example:

```text
Run timeline
backend: complete
UI: partial
gap: useful event grouping
backend change: none
priority: high
```

Do not begin by redesigning everything from screenshots or assumptions.

Use the actual R9 code.

---

# 27. Step 3 — Define Web Console IA

After inspecting the implementation, define the target navigation and page structure.

Prefer reusing existing capabilities.

Do not rename or reorganize backend packages unnecessarily.

The target should be an evolutionary R9 → R10 UI improvement, not another full rewrite.

---

# 28. Step 4 — First UI Milestone

Recommended first implementation milestone:

> **Mission Control Overview**

It should use real R9 data and include:

```text
system status
active runs
run states
worker fleet summary
queue/backlog
pending approvals
recent failures
recent timeline activity
```

No fake demo numbers should remain once connected to the actual Control Plane.

---

# 29. Step 5 — Mission Detail

After Overview:

```text
Mission / Run detail
Execution flow
Timeline
Work items
Artifacts
Review / verification
Approval
Failures
```

This likely provides more operational value than spending time on decorative dashboard elements.

---

# 30. Engineering Constraints

Maintain:

```text
Java 21
Spring Boot 4
Spring Framework 7
Maven
me.asu.aidev
```

Do not change the namespace.

Do not downgrade dependencies.

Do not reintroduce:

```text
com.goldenway
```

The user has explicitly standardized on:

```text
me.asu.aidev
```

---

# 31. Compatibility Philosophy

R9 is the baseline.

Future work does not need compatibility with R1/R2 implementation architecture.

Old R2 logic may still be mined for software-engineering process knowledge.

Reusable R2 process semantics should become standalone workers where appropriate.

Do not resurrect the R2 centralized orchestration model.

---

# 32. Worker Philosophy

Worker implementations may use:

```text
Java
Go
Python
TypeScript
```

Choose pragmatically.

Java remains the Control Plane language.

Workers should remain pull-based and independently deployable.

---

# 33. Product Principle

The goal is not merely an AI coding demo.

The system should behave like an engineering operations platform.

A useful conceptual distinction:

```text
Control Plane
    authority
    state
    scheduling
    visibility

Workers
    execution

Web Console
    operator interaction

Artifacts
    engineering evidence

Timeline
    operational truth

Approval
    human authority
```

Preserve these boundaries.

---

# 34. Development Style

Do not repeatedly ask the user for permission between obvious implementation steps.

When a direction is sufficiently defined:

```text
inspect
analyze
design
implement
test
report
```

Proceed continuously.

Ask questions only where a decision genuinely changes architecture or product behavior.

---

# 35. Testing Requirement

The frozen baseline already passes:

```bash
mvn clean verify
```

New UI work must not break the existing 33 Failsafe integration tests.

Add focused tests for new backend behavior when needed.

For CSS/template-only work, avoid creating meaningless tests purely for coverage.

At meaningful milestones run:

```bash
mvn clean verify
```

and report the result.

---

# 36. Current Priority Order

Use this priority unless actual code inspection reveals a strong reason to adjust it:

```text
1. Inspect existing R9 Web Console
2. Gap analysis
3. Information architecture
4. Mission Control Overview
5. Mission / Run Detail
6. Timeline / Observability UX
7. Fleet / Scheduling UX
8. Human Attention / Approval UX
9. Artifacts / Engineering evidence
10. Visual polish and Nebula design system

Later:
11. AIW Companion
12. Desktop pet / Watchdog visualization
```

---

# 37. What NOT to Do Next

Do not immediately:

```text
rewrite the frontend in React
create a separate Node frontend
rewrite the Control Plane
replace Worker Pull
build another desktop console
focus on shaders / particle effects
create 3D visualization
change persistence schema just for UI convenience
```

First understand and improve the existing R9 Web Console.

---

# 38. Definition of Success for the Next Phase

The next Web Control Plane milestone should make it possible for an operator to open the browser and quickly answer:

```text
Is AIW healthy?

What is running?

Which workers are active?

What is waiting?

What has failed?

What requires my attention?

Why is a run blocked?

What happened during this run?

Which artifacts were produced?

What action can I take now?
```

If those questions can be answered clearly from the browser, the Control Plane is moving in the right direction.

---

# 39. Instruction to the Next Session

Start from the actual R9 Web Console Fix1 source code.

Do not design from this Handoff alone.

First inspect the repository and produce:

```text
1. Current Web Console architecture
2. Existing pages/endpoints
3. Existing domain capabilities already exposed
4. Missing operator workflows
5. UI/UX problems
6. Proposed R10 Web Control Plane roadmap
```

Then proceed to implementation.

The first implementation target should normally be the Mission Control Overview using existing R9 data, unless repository inspection reveals a more fundamental Web Console issue that must be fixed first.