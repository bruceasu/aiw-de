import path from "node:path";
import { EvidenceRecorder } from "./evidence.mjs";
import { ContextAssembler } from "./context.mjs";
import { WorkspaceSession } from "./workspace.mjs";
import { RuntimeFault, checkSignal, failureCategory, errorCode } from "./errors.mjs";

export class AttemptRunner {
  constructor({ timeoutMs = 900000, parentSignal, settleTimeoutMs = 10000, recorder } = {}) {
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1 || !Number.isSafeInteger(settleTimeoutMs) || settleTimeoutMs < 1) {
      throw new RuntimeFault("ATTEMPT_BUDGET_INVALID", "CONFIGURATION");
    }
    this.controller = new AbortController();
    this.signal = parentSignal ? AbortSignal.any([parentSignal, this.controller.signal]) : this.controller.signal;
    this.started = Date.now();
    this.timer = setTimeout(() => this.cancel("deadline"), timeoutMs);
    this.resourceUncertain = false;
    this.reason = null;
    this.active = new Set();
    this.settleTimeoutMs = settleTimeoutMs;
    this.recorder = recorder;
  }
  cancel(reason = "cancelled") {
    this.reason ??= reason;
    const timeout = reason === "deadline" || reason === "tool-timeout";
    this.controller.abort(new RuntimeFault(timeout ? "ATTEMPT_DEADLINE_EXCEEDED" : "ATTEMPT_CANCELLED", timeout ? "TIMEOUT" : "TOOL_ERROR"));
  }
  async initializeEvidence(root, identity, options) {
    return await this.#run(async () => { this.recorder = await EvidenceRecorder.open(root, identity, options); return this.recorder; });
  }
  async step(phase, operation) {
    checkSignal(this.signal);
    if (!this.recorder) throw new RuntimeFault("ATTEMPT_EVIDENCE_REQUIRED", "CONFIGURATION");
    return await this.#run(async () => {
      await this.recorder.record("phase-started", { phase });
      checkSignal(this.signal);
      return await operation();
    });
  }
  async #run(operation) {
    checkSignal(this.signal);
    const promise = Promise.resolve().then(() => { checkSignal(this.signal); return operation(); });
    this.active.add(promise);
    promise.then(() => this.active.delete(promise), () => this.active.delete(promise));
    let graceTimer;
    let cancel;
    const grace = new Promise((_, reject) => {
      cancel = () => { if (!graceTimer) graceTimer = setTimeout(() => { this.resourceUncertain = true; reject(new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN")); }, this.settleTimeoutMs); };
      this.signal.addEventListener("abort", cancel, { once: true });
      if (this.signal.aborted) cancel();
    });
    try { const result = await Promise.race([promise, grace]); checkSignal(this.signal); return result; }
    catch (error) { if (["PROCESS_TREE_UNCERTAIN", "RESOURCE_TERMINATION_UNCERTAIN"].includes(errorCode(error))) this.resourceUncertain = true; throw error; }
    finally { clearTimeout(graceTimer); this.signal.removeEventListener("abort", cancel); }
  }
  async settled(timeoutMs = this.settleTimeoutMs) {
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1) throw new RuntimeFault("ATTEMPT_BUDGET_INVALID", "CONFIGURATION");
    if (!this.active.size) return true;
    let timer;
    try { return await Promise.race([Promise.allSettled([...this.active]).then(() => true), new Promise(resolve => { timer = setTimeout(() => { this.resourceUncertain = true; resolve(false); }, timeoutMs); })]); }
    finally { clearTimeout(timer); }
  }
  dispose() { clearTimeout(this.timer); }
}

async function withinCleanup(promise, timeoutMs, onTimeout = () => {}) {
  let timer;
  try { return await Promise.race([Promise.resolve(promise).then(value => value !== false, () => false),
    new Promise(resolve => { timer = setTimeout(() => { onTimeout(); resolve(false); }, timeoutMs); })]); }
  finally { clearTimeout(timer); }
}

export class ProtocolError extends Error {
  constructor(status) { super(`worker protocol HTTP ${status}`); this.status = status; }
}

export class ControlPlaneClient {
  constructor(cfg, runtimeType) { this.cfg = cfg; this.runtimeType = runtimeType; }
  async register() { return await this.post("/workers/register", { workerId: this.cfg.workerId, runtimeType: this.runtimeType, capabilities: this.cfg.capabilities, maxConcurrency: this.cfg.maxConcurrency }); }
  async workerHeartbeat() { return await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/heartbeat`); }
  async drain() { return await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/drain`); }
  async offline() { return await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/offline`); }
  async claim() { return await this.post("/tasks/claim", { workerId: this.cfg.workerId }, true); }
  async start(task, selection, signal) {
    return await this.post(`/tasks/${encodeURIComponent(task.taskId)}/start`, { workerId: this.cfg.workerId, leaseToken: task.leaseToken,
      ...(selection ? { profileId: selection.id, profileVersion: task.aiGuidance.version, selectionReason: selection.reason } : {}) }, false, signal);
  }
  async heartbeat(task, externalSessionId) { return await this.action(task, "heartbeat", { externalSessionId }); }
  async acknowledgeStop(task) { return await this.action(task, "stop-acknowledgement", {}); }
  async fail(task, externalSessionId, failureReason = null, category = "UNKNOWN") { return await this.action(task, "fail", { externalSessionId, failureReason, failureCategory: category }); }
  async complete(task, externalSessionId, workspaceResult, report, signal) { return await this.action(task, "complete", { externalSessionId, workspaceResult, report }, signal); }
  async action(task, action, fields, signal) { return await this.post(`/tasks/${encodeURIComponent(task.taskId)}/${action}`, { workerId: this.cfg.workerId, leaseToken: task.leaseToken, ...fields }, false, signal); }
  async post(pathPart, payload, allowNoContent = false, signal) {
    const headers = { Accept: "application/json" };
    if (payload !== undefined) headers["Content-Type"] = "application/json";
    if (this.cfg.token) headers.Authorization = `Bearer ${this.cfg.token}`;
    const timeout = AbortSignal.timeout(this.cfg.httpTimeoutMs);
    const response = await fetch(`${this.cfg.baseUrl}/api/worker-protocol/v1${pathPart}`, { method: "POST", headers,
      body: payload === undefined ? undefined : JSON.stringify(payload), signal: signal ? AbortSignal.any([signal, timeout]) : timeout });
    if (response.status === 204 && allowNoContent) return null;
    if (!response.ok) throw new ProtocolError(response.status);
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
}

export async function runClaimedAttempt(cfg, client, task, adapter) {
  const attempt = new AttemptRunner({ timeoutMs: cfg.taskTimeoutMs ?? 900000, parentSignal: cfg.forceSignal, settleTimeoutMs: cfg.cleanupTimeoutMs ?? 10000 });
  let workspace;
  let started = false;
  let ownershipLost = false;
  let stopRequested = false;
  let reporting = false;
  let publicationStarted = false;
  let terminal = "failed";
  let sessionId = null;
  let heartbeatPromise;
  let phase = "TOOL_ERROR";
  let heartbeat;
  let workspaceCleaned = false;
  let workspaceCleanupAttempted = false;
  let evidenceFinished = false;
  let adapterCleared = false;
  let outcome;
  const cleanupTimeoutMs = cfg.cleanupTimeoutMs ?? 10000;
  let cleanupDeadline;
  const uncertain = () => { attempt.resourceUncertain = true; };
  const remainingCleanup = () => { cleanupDeadline ??= Date.now() + cleanupTimeoutMs; return Math.max(0, cleanupDeadline - Date.now()); };
  const cleanupWait = operation => {
    const remaining = remainingCleanup();
    if (!remaining) { uncertain(); return Promise.resolve(false); }
    return withinCleanup(Promise.resolve().then(operation), remaining, uncertain);
  };
  const settleAdapter = () => withinCleanup(Promise.resolve().then(() => adapter.settled?.(cleanupTimeoutMs)), cleanupTimeoutMs, uncertain);
  try {
    const evidenceRoot = cfg.evidenceRoot ?? path.resolve(cfg.repositoryRoot || cfg.fallbackWorkspace, "..", ".aidev-attempt-evidence");
    await attempt.initializeEvidence(evidenceRoot, { taskId: task.taskId, attemptId: task.attemptId, attemptNo: task.attemptNo, role: cfg.role, runtime: adapter.runtime,
      versions: adapter.versions ?? {} }, { maxBytes: cfg.evidenceMaxBytes ?? 4 * 1024 * 1024, forbiddenRoots: [cfg.repositoryRoot, cfg.fallbackWorkspace, cfg.worktreeRoot] });
    await attempt.step("evidence-retention", () => EvidenceRecorder.prune(evidenceRoot));
    const selection = adapter.select(task);
    await attempt.step("start", async () => { await client.start(task, selection ?? undefined, attempt.signal); started = true; });
    heartbeat = setInterval(() => {
      if (heartbeatPromise || ownershipLost || reporting) return;
      heartbeatPromise = client.heartbeat(task, sessionId).then(response => {
        if (response?.stopRequested) { stopRequested = true; attempt.cancel("stop-requested"); }
      }).catch(async () => {
        ownershipLost = true;
        attempt.cancel("lease-lost");
        await attempt.recorder.record("lease-lost", { reason: "lease-lost", status: "unknown" });
      }).finally(() => { heartbeatPromise = undefined; });
      // The awaited heartbeat below observes failures, while this prevents an unhandled rejection during execution.
      heartbeatPromise.catch(() => attempt.cancel("critical-evidence"));
    }, cfg.heartbeatMs);
    const profile = adapter.profile(task);
    if (profile.resultKind === "WORKSPACE" && (!task.workspace || !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(task.workspace.baseRevision ?? ""))) {
      throw new RuntimeFault("WRITABLE_WORKSPACE_REQUIRED", "CONFIGURATION");
    }
    if (profile.resultKind === "VERDICT") {
      const revisions = new Set((task.inputs ?? []).map(input => input.resultRevision).filter(Boolean));
      if (revisions.size && (revisions.size !== 1 || !revisions.has(task.workspace?.baseRevision))) {
        throw new RuntimeFault("UPSTREAM_REVISION_MISMATCH", "CONFIGURATION");
      }
    }
    const context = new ContextAssembler({ maxInputBytes: cfg.maxInputBytes ?? 1024 * 1024, recorder: attempt.recorder, signal: attempt.signal });
    context.source(`cp-inputs:${task.taskId}`, JSON.stringify({ taskType: task.taskType, instruction: task.instruction, skillRef: task.skillRef, inputs: task.inputs }), "control-plane-inputs");
    const prompt = adapter.prompt(task, context);
    await attempt.step("prepare", async () => {
      workspace = await WorkspaceSession.materialize(cfg, task, attempt);
      await workspace.beforeExecution(profile.readOnly);
    });
    phase = "MODEL_ERROR";
    const result = await attempt.step("execute", () => adapter.execute({ task, workspace, profile, prompt, context, attempt, selection: selection?.profile,
      onSession: id => { sessionId = id; } }));
    if (!await attempt.step("resource-settlement", settleAdapter)) { attempt.resourceUncertain = true; throw new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN"); }
    checkSignal(attempt.signal);
    adapter.assertSuccessful?.();
    phase = "OUTPUT_INVALID";
    const report = await attempt.step("validate-report", async () => {
      const value = adapter.report(profile.resultKind, result);
      await workspace.validateReadOnly(profile.readOnly);
      await attempt.recorder.record("report-validated", { verdict: value?.verdict ?? "not-required", digest: adapter.digest?.(result), status: "valid" });
      return value;
    });
    phase = "TOOL_ERROR";
    publicationStarted = true;
    const workspaceResult = await attempt.step("publish", () => workspace.finalize(task, profile.readOnly));
    if (profile.resultKind === "WORKSPACE" && (!workspaceResult || !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/i.test(workspaceResult.resultRevision) || !Array.isArray(workspaceResult.changedFiles))) {
      throw new RuntimeFault("WORKSPACE_RESULT_INVALID", "OUTPUT_INVALID");
    }
    clearInterval(heartbeat);
    if (heartbeatPromise) await attempt.step("heartbeat-settlement", () => heartbeatPromise);
    checkSignal(attempt.signal);
    attempt.recorder.assertHealthy();
    await attempt.step("prepare-report", () => attempt.recorder.record("report-posting", { reportStatus: "pending", resultRevision: workspaceResult?.resultRevision, publicationRef: workspaceResult?.publicationRef }));
    // Save required terminal/cleanup evidence before the non-idempotent success request.
    await attempt.step("cleanup-before-report", async () => {
      workspaceCleanupAttempted = true;
      await workspace.cleanup();
      if (attempt.resourceUncertain) throw new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN");
      workspaceCleaned = true;
      adapter.clear?.();
      adapterCleared = true;
      await attempt.recorder.record("resource-terminal", { status: "settled" });
      await attempt.recorder.finish("ready-to-report");
      evidenceFinished = true;
    });
    checkSignal(attempt.signal);
    // CP complete/fail are not idempotent. An uncertain complete response must never trigger fail or rerun.
    await attempt.step("report", async () => {
      reporting = true;
      await client.complete(task, sessionId, workspaceResult, report, attempt.signal);
      await attempt.recorder.record("report-posted", { reportStatus: "accepted" });
    });
    terminal = "completed";
    return outcome = { status: terminal, sessionId, workspaceResult, resourceUncertain: false };
  } catch (error) {
    workspace ??= error.workspace;
    if (!started && error instanceof ProtocolError) {
      try { stopRequested = Boolean((await client.heartbeat(task, sessionId))?.stopRequested); } catch { /* lease may already be gone */ }
    }
    if (["PROCESS_TREE_UNCERTAIN", "RESOURCE_TERMINATION_UNCERTAIN"].includes(errorCode(error))) attempt.resourceUncertain = true;
    terminal = stopRequested ? "stopped" : ownershipLost ? "lease-lost" : reporting ? "report-unknown" : "failed";
    attempt.cancel(attempt.reason ?? errorCode(error));
    await cleanupWait(() => attempt.recorder?.record("attempt-failed", { status: terminal, category: failureCategory(error, phase), code: errorCode(error), reason: attempt.reason, reportStatus: reporting ? "unknown" : "not-completed" }));
    if (started && !stopRequested && !ownershipLost && !reporting) {
      const posted = await cleanupWait(() => client.fail(task, sessionId, errorCode(error), failureCategory(error, phase)));
      await cleanupWait(() => attempt.recorder?.record("failure-posted", { reportStatus: posted ? "accepted" : "unknown" }));
    }
    // Logs contain status/code only; no task body, source, lease token or raw SDK errors.
    console.error(`attempt ${task.attemptId}: ${terminal} ${errorCode(error)}`);
    return outcome = { status: terminal, sessionId, evidence: attempt.recorder?.directory, resourceUncertain: attempt.resourceUncertain };
  } finally {
    clearInterval(heartbeat);
    if (terminal !== "completed") attempt.cancel("attempt-ending");
    const settled = await Promise.all([
      cleanupWait(() => adapter.settled?.(remainingCleanup())),
      cleanupWait(() => attempt.settled(Math.max(1, remainingCleanup()))),
      cleanupWait(() => heartbeatPromise),
    ]);
    if (settled.some(value => !value)) attempt.resourceUncertain = true;
    if (!workspaceCleaned && !workspaceCleanupAttempted && !await cleanupWait(() => workspace?.cleanup())) attempt.resourceUncertain = true;
    // A live adapter retains its host, history and resource leases until an operator can reconcile it.
    if (!attempt.resourceUncertain && !adapterCleared) {
      try { adapter.clear?.(); } catch { attempt.resourceUncertain = true; }
    }
    if (attempt.resourceUncertain) {
      await cleanupWait(() => attempt.recorder?.record("resource-terminal", { status: "unknown", code: "PROCESS_TREE_UNCERTAIN" }));
    } else if (!evidenceFinished) {
      if (!await cleanupWait(() => attempt.recorder?.finish(terminal))) console.error(`attempt ${task.attemptId}: CRITICAL_EVIDENCE_OR_CLEANUP_FAILED`);
    }
    if (stopRequested && !publicationStarted && !attempt.resourceUncertain && !ownershipLost && !reporting) {
      try { await client.acknowledgeStop(task); } catch { /* Control Plane may have resolved the race */ }
    }
    if (outcome) outcome.resourceUncertain = attempt.resourceUncertain;
    attempt.dispose();
  }
}

/** A retired slot never claims again, including when its observer or drain callback fails. */
export async function runWorkerSlot({ cfg, client, createAdapter, shouldStop, onOutcome, onUncertain, onError }) {
  let retired = false;
  const wait = () => new Promise(resolve => setTimeout(resolve, cfg.pollMs));
  while (!retired && !shouldStop() && !cfg.forceSignal?.aborted) {
    try {
      const task = await client.claim();
      if (cfg.forceSignal?.aborted) return;
      if (!task) { await wait(); continue; }
      const outcome = await runClaimedAttempt(cfg, client, task, createAdapter(task));
      retired = outcome.resourceUncertain;
      if (retired && !await withinCleanup(Promise.resolve().then(() => onUncertain?.(outcome, task)), cfg.cleanupTimeoutMs ?? 10000)) {
        onError?.(new RuntimeFault("SLOT_DRAIN_UNCONFIRMED"));
      }
      onOutcome?.(outcome, task);
    } catch (error) {
      onError?.(error);
      if (!retired) await wait();
    }
  }
}
