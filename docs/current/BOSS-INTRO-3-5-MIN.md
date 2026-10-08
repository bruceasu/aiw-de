# 3–5 Minute Boss Introduction

This is a speaking script, not a detailed architecture review.

## 30 seconds — What it is

> We are building an AI Development Manager. The idea is to move beyond using Codex or another AI coding tool as a single developer assistant. Instead, we provide a Control Plane that can manage multiple AI workers through the normal engineering lifecycle: requirement analysis, architecture, planning, coding, review, verification, repair and human approval.

## 45 seconds — Why we need it

> AI coding models are already good at implementing individual tasks, but a real software team needs more than code generation. We need durable task state, dependency management, retries after worker crashes, parallel work without developers overwriting each other, independent review and verification, human approval for sensitive decisions, and an audit trail of exactly which Git revision was accepted. The AI Development Manager is designed to provide this engineering control layer.

## 60 seconds — How it works

> A user submits a natural-language requirement. The Control Plane creates engineering tasks rather than directly calling a fixed sequence of agents. AI workers register their capabilities and pull compatible tasks when they are free. This means we can add more workers at any time, and workers can be implemented in Java, Go, Python, JavaScript or TypeScript.
>
> Coding workers use isolated Git worktrees. If a plan can be safely split, multiple workers can code in parallel and the system creates an integration task before final review. Review and verification are independent. If they find a problem, they request a Repair task instead of modifying the code themselves.

## 45 seconds — Control and safety

> The Java Control Plane remains authoritative. A model cannot simply declare itself successful. Task ownership is protected by leases and attempt history. The final code result is identified by an immutable Git commit. Review and verification produce a formal Engineering Gate, and a separate Human Approval can be required. We also support Telegram Approve/Reject as one interaction channel, while the approval record remains in the Control Plane.

## 45 seconds — Operations and scalability

> The system is local-first: SQLite is enough for development, while PostgreSQL is supported for shared/server operation. Workers use a pull model, so adding AI capacity does not require changing a central pipeline. We already have priority, fairness, worker concurrency, per-project-run concurrency, a global Fleet limit, pause/resume/cancel/recovery, dashboards and an audit timeline.

## 30 seconds — Current status and next step

> The accepted execution platform is now exposed through the R9 Web Console. Users can create requirements, monitor runs, recover failures, adjust concurrency, manage workers and decide approvals without working directly with REST APIs. The next priority is real end-to-end Codex development on a small repository, followed by PostgreSQL concurrency/security hardening and PR/release delivery.

## One-sentence summary

> In short: Codex is an AI developer; AI Dev Manager is the engineering organization and control system around a workforce of AI developers.

## Optional 60-second live demo

If a live demo is appropriate:

1. show `POST /api/r8/requirements` with one small requirement;
2. show the Run Dashboard as tasks move through phases;
3. show two implementation tasks being claimed by different workers if available;
4. show Review + Verification and the Engineering Gate;
5. show an approval action/API (or Telegram button);
6. finish with the Run in `SUCCEEDED` and the exact `resultRevision`.

Avoid demoing configuration internals unless asked; focus on the lifecycle and authority model.
