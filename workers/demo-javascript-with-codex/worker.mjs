import { Codex } from "@openai/codex-sdk";
import { execFile } from "node:child_process";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";
const execFileAsync = promisify(execFile);

class ProtocolError extends Error { constructor(status, message) { super(`worker protocol HTTP ${status}: ${message}`); this.status = status; } }

const repositoryRef = env("AIW_REPOSITORY_REF", "").trim();
if (env("AIW_GIT_PUBLISH_REMOTE", "").trim()) throw new Error("AIW_GIT_PUBLISH_REMOTE is forbidden: Workers cannot git push");
if (/^(1|true|yes)$/i.test(env("AIW_CODEX_NETWORK_ACCESS", "false"))) throw new Error("AIW_CODEX_NETWORK_ACCESS is forbidden for Workers");
const repositoryRoot = env("AIW_REPOSITORY_ROOT", "").trim();
if ((repositoryRef === "") !== (repositoryRoot === "")) throw new Error("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together");
const capabilities = new Set(env("AIW_WORKER_CAPABILITIES", "coding").split(",").map(x => x.trim()).filter(Boolean));
if (repositoryRef) capabilities.add(`repo:${repositoryRef}`);
const fallbackWorkspace = env("AIW_WORKSPACE", repositoryRoot || process.cwd());
const cfg = {
  baseUrl: env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080").replace(/\/$/, ""),
  workerId: env("AIW_WORKER_ID", "codex-javascript-1"), token: env("AIW_WORKER_TOKEN", ""),
  capabilities: [...capabilities].sort(), maxConcurrency: positiveInt("AIW_WORKER_MAX_CONCURRENCY", 1),
  fallbackWorkspace, repositoryRef, repositoryRoot,
  worktreeRoot: env("AIW_WORKTREE_ROOT", path.resolve(repositoryRoot || fallbackWorkspace, "..", ".aidev-worktrees")),
  pollMs: positiveInt("AIW_WORKER_POLL_MS", 1000), heartbeatMs: positiveInt("AIW_WORKER_HEARTBEAT_MS", 20000),
  model: process.env.AIW_CODEX_MODEL || undefined, sandboxMode: env("AIW_CODEX_SANDBOX", "workspace-write"),
  approvalPolicy: env("AIW_CODEX_APPROVAL_POLICY", "never"), reasoningEffort: env("AIW_CODEX_REASONING_EFFORT", "high"),
  networkAccess: false,
};

class Client {
  async post(pathPart, payload, allowNoContent = false) {
    const headers = { Accept: "application/json" }; if (payload !== undefined) headers["Content-Type"] = "application/json"; if (cfg.token) headers.Authorization = `Bearer ${cfg.token}`;
    const response = await fetch(`${cfg.baseUrl}/api/worker-protocol/v1${pathPart}`, { method: "POST", headers, body: payload === undefined ? undefined : JSON.stringify(payload) });
    if (response.status === 204 && allowNoContent) return null; if (!response.ok) throw new ProtocolError(response.status, await response.text());
    const text = await response.text(); return text ? JSON.parse(text) : null;
  }
  register() { return this.post("/workers/register", { workerId: cfg.workerId, runtimeType: "codex-javascript-sdk", capabilities: cfg.capabilities, maxConcurrency: cfg.maxConcurrency }); }
  workerHeartbeat() { return this.post(`/workers/${encodeURIComponent(cfg.workerId)}/heartbeat`); }
  drain() { return this.post(`/workers/${encodeURIComponent(cfg.workerId)}/drain`); }
  offline() { return this.post(`/workers/${encodeURIComponent(cfg.workerId)}/offline`); }
  claim() { return this.post("/tasks/claim", { workerId: cfg.workerId }, true); }
  action(task, action, externalSessionId = null) { return this.post(`/tasks/${encodeURIComponent(task.taskId)}/${action}`, { workerId: cfg.workerId, leaseToken: task.leaseToken, externalSessionId }); }
  fail(task, externalSessionId = null, failureReason = null) { return this.post(`/tasks/${encodeURIComponent(task.taskId)}/fail`, { workerId: cfg.workerId, leaseToken: task.leaseToken, externalSessionId, failureReason }); }
  complete(task, externalSessionId, workspaceResult, report) { return this.post(`/tasks/${encodeURIComponent(task.taskId)}/complete`, { workerId: cfg.workerId, leaseToken: task.leaseToken, externalSessionId, workspaceResult, report }); }
}

class GitWorkspace {
  constructor(cwd, assignment) { this.cwd = cwd; this.assignment = assignment; }
  static async materialize(task) {
    if (!task.workspace) return new GitWorkspace(cfg.fallbackWorkspace, null);
    if (!cfg.repositoryRef || !cfg.repositoryRoot) throw new Error("repository task claimed without repository configuration");
    if (task.workspace.repositoryRef !== cfg.repositoryRef) throw new Error(`repository mismatch: ${task.workspace.repositoryRef} != ${cfg.repositoryRef}`);
    await fetchPublishedInputs(task);
    const resolved = (await git(cfg.repositoryRoot, "rev-parse", "--verify", `${task.workspace.baseRevision}^{commit}`)).trim().toLowerCase();
    if (resolved !== task.workspace.baseRevision.toLowerCase()) throw new Error("base revision mismatch");
    await mkdir(cfg.worktreeRoot, { recursive: true });
    const cwd = path.resolve(cfg.worktreeRoot, task.workspace.workspaceKey);
    await git(cfg.repositoryRoot, "worktree", "add", "-b", task.workspace.branchName, cwd, task.workspace.baseRevision);
    return new GitWorkspace(cwd, task.workspace);
  }
  async finalize(task) {
    if (!this.assignment) return null;
    await git(this.cwd, "add", "-A");
    const { stdout: staged } = await execFileAsync("git", ["-C", this.cwd, "diff", "--cached", "--name-only", "-z"], { encoding: "buffer" });
    if (staged.length) await execFileAsync("git", ["-C", this.cwd, "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost", "commit", "--no-gpg-sign", "-m", `aidev: ${task.taskId} attempt ${task.attemptNo}`]);
    const resultRevision = (await git(this.cwd, "rev-parse", "HEAD")).trim().toLowerCase();
    const { stdout } = await execFileAsync("git", ["-C", this.cwd, "diff", "--name-only", "-z", this.assignment.baseRevision, resultRevision], { encoding: "buffer" });
    const changedFiles = stdout.toString("utf8").split("\0").filter(Boolean).sort();
    return { resultRevision, changedFiles, publicationRef: null };
  }
  async cleanup() { if (this.assignment) try { await git(cfg.repositoryRoot, "worktree", "remove", "--force", this.cwd); } catch (e) { console.error("worktree cleanup failed", e); } }
}
async function fetchPublishedInputs(task) {
  if (!cfg.repositoryRoot) return;
  for (const input of task.inputs || []) {
    const ref = input.publicationRef || "";
    if (!ref.startsWith("git:")) continue;
    const parts = ref.split(":", 3);
    if (parts.length !== 3) continue;
    await git(cfg.repositoryRoot, "fetch", parts[1], parts[2]);
  }
}
async function git(cwd, ...args) { if (args.includes("push")) throw new Error("GIT_PUSH_FORBIDDEN"); const { stdout } = await execFileAsync("git", ["-C", cwd, ...args], { encoding: "utf8" }); return stdout; }

const client = new Client(); let stopping = false;
process.on("SIGINT", () => { stopping = true; }); process.on("SIGTERM", () => { stopping = true; });
await client.register(); console.log(`registered ${cfg.workerId} (${cfg.maxConcurrency} slots)`);
const workerHeartbeat = setInterval(() => void client.workerHeartbeat().catch(e => console.error("worker heartbeat failed", e)), cfg.heartbeatMs);
await Promise.all(Array.from({ length: cfg.maxConcurrency }, (_, i) => slotLoop(i + 1))); clearInterval(workerHeartbeat);
try { await client.drain(); } catch {} try { await client.offline(); } catch {}

async function slotLoop(slot) { const codex = new Codex(); while (!stopping) { try { const task = await client.claim(); if (!task) { await sleep(cfg.pollMs); continue; } await executeTask(slot, codex, task); } catch (e) { console.error(`slot ${slot}:`, e); await sleep(cfg.pollMs); } } }
async function executeTask(slot, codex, task) {
  await client.action(task, "start"); let workspace = null; let threadId = null; const abort = new AbortController(); let done = false; let ownershipLost = false; let finalResponse = "";
  const heartbeat = (async () => { while (!done && !stopping) { await sleep(cfg.heartbeatMs); if (done || stopping) break; try { await client.action(task, "heartbeat", threadId); } catch (e) { ownershipLost = true; abort.abort(); console.error(`task ${task.taskId}: lease lost`, e); break; } } })();
  try {
    workspace = await GitWorkspace.materialize(task);
    const thread = codex.startThread({ workingDirectory: workspace.cwd, sandboxMode: cfg.sandboxMode, approvalPolicy: cfg.approvalPolicy, modelReasoningEffort: cfg.reasoningEffort, networkAccessEnabled: cfg.networkAccess, ...(cfg.model ? { model: cfg.model } : {}) });
    threadId = thread.id; const effectiveInstruction = task.inputs?.length ? `${task.instruction}\n\nUpstream task inputs (authoritative):\n${JSON.stringify(task.inputs, null, 2)}` : task.instruction; const { events } = await thread.runStreamed(effectiveInstruction, { signal: abort.signal });
    for await (const event of events) { if (event.type === "thread.started") { threadId = event.thread_id; try { await client.action(task, "heartbeat", threadId); } catch (e) { ownershipLost = true; abort.abort(); throw e; } } else if (event.type === "item.completed" && event.item.type === "agent_message") finalResponse = event.item.text; else if (event.type === "turn.failed") throw new Error(event.error.message); }
    if (!ownershipLost) { const workspaceResult = await workspace.finalize(task); const report = completionReport(task, finalResponse); await client.complete(task, threadId, workspaceResult, report); console.log(`slot ${slot}: completed ${task.taskId} revision=${workspaceResult?.resultRevision ?? "-"}`); if (finalResponse) console.log(finalResponse); }
  } catch (e) { if (!ownershipLost) { try { await client.fail(task, threadId, String(e)); } catch (reportError) { console.error("fail reporting failed", reportError); } console.error(`slot ${slot}: task ${task.taskId} failed`, e); } }
  finally { done = true; await heartbeat; if (workspace) await workspace.cleanup(); }
}
function completionReport(task, finalResponse) {
  if (new Set(["REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING"]).has(task.taskType)) {
    const summary = finalResponse.trim();
    if (!summary) throw new Error(`${task.taskType} must return a non-empty final response`);
    return { verdict: "PASS", summary };
  }
  if (!new Set(["CODE_REVIEW", "VERIFICATION", "INTEGRATION_VERIFICATION"]).has(task.taskType)) return null;
  const lines = finalResponse.split(/\r?\n/).map(x => x.trim()).filter(Boolean);
  const marker = [...lines].reverse().find(x => x.startsWith("AIW_VERDICT:"));
  const verdict = marker?.slice("AIW_VERDICT:".length).trim();
  if (!new Set(["PASS", "REPAIR_REQUIRED"]).has(verdict)) throw new Error("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED");
  return { verdict, summary: finalResponse.trim() || `AIW_VERDICT: ${verdict}` };
}

function env(name, fallback) { return process.env[name] ?? fallback; }
function positiveInt(name, fallback) { const n = Number.parseInt(process.env[name] ?? String(fallback), 10); if (!Number.isFinite(n) || n < 1) throw new Error(`${name} must be >= 1`); return n; }
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
