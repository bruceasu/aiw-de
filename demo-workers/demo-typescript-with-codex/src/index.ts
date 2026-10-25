import { Codex } from "@openai/codex-sdk";
import type { ApprovalMode, ModelReasoningEffort, SandboxMode } from "@openai/codex-sdk";
import { execFile } from "node:child_process";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

interface WorkspaceAssignment {
  repositoryRef: string;
  baseRevision: string | null;
  workspaceKey: string;
  branchName: string | null;
  repositoryPath?: string;
  workspaceId?: string;
  workspaceRoot?: string;
}

interface WorkspaceResult {
  resultRevision: string;
  changedFiles: string[];
  publicationRef: string | null;
}

interface TaskInput {
  taskId: string; taskType: string; repositoryRef: string | null; baseRevision: string | null;
  resultRevision: string | null; publicationRef: string | null; changedFiles: string[];
  completionVerdict: "PASS" | "REPAIR_REQUIRED" | null; completionSummary: string | null;
}

interface CompletionReport {
  verdict: "PASS" | "REPAIR_REQUIRED";
  summary: string;
}

interface ClaimedTask {
  taskId: string;
  taskType: string;
  subjectType: string;
  subjectId: string;
  requiredCapabilities: string[];
  skillRef: string | null;
  instruction: string;
  priority: number;
  attemptId: string;
  attemptNo: number;
  leaseToken: string;
  leaseAcquiredAt: string;
  leaseExpiresAt: string;
  workspace: WorkspaceAssignment | null;
  inputs: TaskInput[];
}

interface Config {
  baseUrl: string;
  workerId: string;
  token: string;
  capabilities: string[];
  maxConcurrency: number;
  fallbackWorkspace: string;
  repositoryRef: string;
  repositoryRoot: string;
  worktreeRoot: string;
  pollMs: number;
  heartbeatMs: number;
  model?: string;
  sandboxMode: SandboxMode;
  approvalPolicy: ApprovalMode;
  reasoningEffort: ModelReasoningEffort;
  networkAccess: boolean;
}

class ProtocolError extends Error {
  constructor(readonly status: number, message: string) {
    super(`worker protocol HTTP ${status}: ${message}`);
  }
}

class ControlPlaneClient {
  constructor(private readonly cfg: Config) {}

  async register(): Promise<void> {
    await this.post("/workers/register", {
      workerId: this.cfg.workerId,
      runtimeType: "codex-typescript-sdk",
      capabilities: this.cfg.capabilities,
      maxConcurrency: this.cfg.maxConcurrency,
    });
  }
  async workerHeartbeat(): Promise<void> { await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/heartbeat`); }
  async drain(): Promise<void> { await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/drain`); }
  async offline(): Promise<void> { await this.post(`/workers/${encodeURIComponent(this.cfg.workerId)}/offline`); }
  async claim(): Promise<ClaimedTask | null> { return await this.post<ClaimedTask>("/tasks/claim", { workerId: this.cfg.workerId }, true); }
  async start(task: ClaimedTask): Promise<void> { await this.taskAction(task, "start"); }
  async heartbeat(task: ClaimedTask, externalSessionId: string | null): Promise<void> { await this.taskAction(task, "heartbeat", externalSessionId); }
  async fail(task: ClaimedTask, externalSessionId: string | null, failureReason: string | null = null): Promise<void> {
    await this.post(`/tasks/${encodeURIComponent(task.taskId)}/fail`, {
      workerId: this.cfg.workerId, leaseToken: task.leaseToken, externalSessionId, failureReason,
    });
  }
  async complete(task: ClaimedTask, externalSessionId: string | null, workspaceResult: WorkspaceResult | null, report: CompletionReport | null): Promise<void> {
    await this.post(`/tasks/${encodeURIComponent(task.taskId)}/complete`, {
      workerId: this.cfg.workerId,
      leaseToken: task.leaseToken,
      externalSessionId,
      workspaceResult,
      report,
    });
  }
  private async taskAction(task: ClaimedTask, action: string, externalSessionId: string | null = null): Promise<void> {
    await this.post(`/tasks/${encodeURIComponent(task.taskId)}/${action}`, {
      workerId: this.cfg.workerId,
      leaseToken: task.leaseToken,
      externalSessionId,
    });
  }
  private async post<T = unknown>(pathPart: string, payload?: unknown, allowNoContent = false): Promise<T | null> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (payload !== undefined) headers["Content-Type"] = "application/json";
    if (this.cfg.token) headers.Authorization = `Bearer ${this.cfg.token}`;
    const response = await fetch(`${this.cfg.baseUrl}/api/r1/worker-protocol/v1${pathPart}`, {
      method: "POST", headers, body: payload === undefined ? undefined : JSON.stringify(payload),
    });
    if (response.status === 204 && allowNoContent) return null;
    if (!response.ok) throw new ProtocolError(response.status, await response.text());
    const text = await response.text();
    return text ? (JSON.parse(text) as T) : null;
  }
}

class GitWorkspace {
  private constructor(readonly cwd: string, readonly assignment: WorkspaceAssignment | null, private readonly cfg: Config,
                      private readonly repositoryRoot: string = cfg.repositoryRoot) {}

  static async materialize(cfg: Config, task: ClaimedTask): Promise<GitWorkspace> {
    if (!task.workspace) return new GitWorkspace(cfg.fallbackWorkspace, null, cfg);
    const repositoryRoot = task.workspace.repositoryPath || cfg.repositoryRoot;
    if (!repositoryRoot) throw new Error("repository task has no directory path");
    if (!task.workspace.repositoryPath && task.workspace.repositoryRef !== cfg.repositoryRef) throw new Error(`repository mismatch: task=${task.workspace.repositoryRef} worker=${cfg.repositoryRef}`);
    if (!task.workspace.baseRevision) return new GitWorkspace(path.resolve(repositoryRoot), task.workspace, cfg, repositoryRoot);
    await fetchPublishedInputs(repositoryRoot, task);
    const resolved = (await git(repositoryRoot, "rev-parse", "--verify", `${task.workspace.baseRevision}^{commit}`)).trim().toLowerCase();
    if (resolved !== task.workspace.baseRevision.toLowerCase()) throw new Error(`base revision mismatch: expected ${task.workspace.baseRevision}, resolved ${resolved}`);
    await mkdir(cfg.worktreeRoot, { recursive: true });
    const cwd = path.resolve(cfg.worktreeRoot, task.workspace.workspaceKey);
    await git(repositoryRoot, "worktree", "add", "-b", task.workspace.branchName!, cwd, task.workspace.baseRevision);
    return new GitWorkspace(cwd, task.workspace, cfg, repositoryRoot);
  }

  async finalize(task: ClaimedTask): Promise<WorkspaceResult | null> {
    if (!this.assignment?.baseRevision) return null;
    await git(this.cwd, "add", "-A");
    const { stdout: staged } = await execFileAsync("git", ["-C", this.cwd, "diff", "--cached", "--name-only", "-z"], { encoding: "buffer" });
    if (staged.length > 0) {
      await execFileAsync("git", ["-C", this.cwd,
        "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost",
        "commit", "--no-gpg-sign", "-m", `aidev: ${task.taskId} attempt ${task.attemptNo}`]);
    }
    const resultRevision = (await git(this.cwd, "rev-parse", "HEAD")).trim().toLowerCase();
    const { stdout } = await execFileAsync("git", ["-C", this.cwd, "diff", "--name-only", "-z", this.assignment.baseRevision, resultRevision], { encoding: "buffer" });
    const changedFiles = stdout.toString("utf8").split("\0").filter(Boolean).sort();
    return { resultRevision, changedFiles, publicationRef: null };
  }

  async cleanup(): Promise<void> {
    if (!this.assignment?.baseRevision) return;
    try { await git(this.repositoryRoot, "worktree", "remove", "--force", this.cwd); } catch (e) { console.error(`worktree cleanup failed ${this.cwd}`, e); }
  }
}

async function fetchPublishedInputs(repositoryRoot: string, task: ClaimedTask): Promise<void> {
  for (const input of task.inputs ?? []) {
    const ref = input.publicationRef ?? "";
    if (!ref.startsWith("git:")) continue;
    const parts = ref.split(":", 3);
    if (parts.length !== 3) continue;
    await git(repositoryRoot, "fetch", parts[1]!, parts[2]!);
  }
}

async function git(cwd: string, ...args: string[]): Promise<string> {
  if (args.includes("push")) throw new Error("GIT_PUSH_FORBIDDEN");
  const { stdout } = await execFileAsync("git", ["-C", cwd, ...args], { encoding: "utf8" });
  return stdout;
}

const cfg = loadConfig();
const client = new ControlPlaneClient(cfg);
let stopping = false;
for (const sig of ["SIGINT", "SIGTERM"] as const) process.on(sig, () => { stopping = true; });

await client.register();
console.log(`registered ${cfg.workerId} (${cfg.maxConcurrency} slots)`);
const workerHeartbeat = setInterval(() => void client.workerHeartbeat().catch((e) => console.error("worker heartbeat failed", e)), cfg.heartbeatMs);
await Promise.all(Array.from({ length: cfg.maxConcurrency }, (_, i) => slotLoop(i + 1)));
clearInterval(workerHeartbeat);
try { await client.drain(); } catch {}
try { await client.offline(); } catch {}

async function slotLoop(slot: number): Promise<void> {
  const codex = new Codex();
  while (!stopping) {
    try {
      const task = await client.claim();
      if (!task) { await sleep(cfg.pollMs); continue; }
      await executeTask(slot, codex, task);
    } catch (error) {
      console.error(`slot ${slot}:`, error);
      await sleep(cfg.pollMs);
    }
  }
}

async function executeTask(slot: number, codex: Codex, task: ClaimedTask): Promise<void> {
  await client.start(task);
  let workspace: GitWorkspace | null = null;
  let threadId: string | null = null;
  const abort = new AbortController();
  let done = false;
  let ownershipLost = false;
  let finalResponse = "";
  const heartbeat = (async () => {
    while (!done && !stopping) {
      await sleep(cfg.heartbeatMs);
      if (done || stopping) break;
      try { await client.heartbeat(task, threadId); }
      catch (error) { ownershipLost = true; abort.abort(); console.error(`task ${task.taskId}: lease heartbeat lost`, error); break; }
    }
  })();

  try {
    workspace = await GitWorkspace.materialize(cfg, task);
    const thread = codex.startThread({
      workingDirectory: workspace.cwd,
      sandboxMode: cfg.sandboxMode,
      approvalPolicy: cfg.approvalPolicy,
      modelReasoningEffort: cfg.reasoningEffort,
      networkAccessEnabled: cfg.networkAccess,
      ...(cfg.model ? { model: cfg.model } : {}),
    });
    threadId = thread.id;
    const effectiveInstruction = task.inputs?.length ? `${task.instruction}\n\nUpstream task inputs (authoritative):\n${JSON.stringify(task.inputs, null, 2)}` : task.instruction;
    const streamed = await thread.runStreamed(effectiveInstruction, { signal: abort.signal });
    for await (const event of streamed.events) {
      if (event.type === "thread.started") {
        threadId = event.thread_id;
        try { await client.heartbeat(task, threadId); }
        catch (error) { ownershipLost = true; abort.abort(); throw error; }
      } else if (event.type === "item.completed" && event.item.type === "agent_message") finalResponse = event.item.text;
      else if (event.type === "turn.failed") throw new Error(event.error.message);
    }
    if (!ownershipLost) {
      const workspaceResult = await workspace.finalize(task);
      const report = completionReport(task, finalResponse);
      await client.complete(task, threadId, workspaceResult, report);
      console.log(`slot ${slot}: completed ${task.taskId} session=${threadId ?? "-"} revision=${workspaceResult?.resultRevision ?? "-"}`);
      if (finalResponse) console.log(`slot ${slot}: ${finalResponse}`);
    }
  } catch (error) {
    if (!ownershipLost) {
      try { await client.fail(task, threadId, String(error)); } catch (reportError) { console.error(`task ${task.taskId}: fail reporting failed`, reportError); }
      console.error(`slot ${slot}: task ${task.taskId} failed`, error);
    }
  } finally {
    done = true;
    await heartbeat;
    if (workspace) await workspace.cleanup();
  }
}

function completionReport(task: ClaimedTask, finalResponse: string): CompletionReport | null {
  if (["REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING"].includes(task.taskType)) {
    const summary = finalResponse.trim();
    if (!summary) throw new Error(`${task.taskType} must return a non-empty final response`);
    return { verdict: "PASS", summary };
  }
  if (task.taskType !== "CODE_REVIEW" && task.taskType !== "VERIFICATION" && task.taskType !== "INTEGRATION_VERIFICATION") return null;
  const marker = [...finalResponse.split(/\r?\n/)].reverse().map(x => x.trim()).find(x => x.startsWith("AIW_VERDICT:"));
  const verdict = marker?.slice("AIW_VERDICT:".length).trim();
  if (verdict !== "PASS" && verdict !== "REPAIR_REQUIRED") {
    throw new Error("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED");
  }
  return { verdict, summary: finalResponse.trim() || `AIW_VERDICT: ${verdict}` };
}

function loadConfig(): Config {
  if (env("AIW_GIT_PUBLISH_REMOTE", "").trim()) throw new Error("AIW_GIT_PUBLISH_REMOTE is forbidden: Workers cannot git push");
  if (/^(1|true|yes)$/i.test(env("AIW_CODEX_NETWORK_ACCESS", "false"))) throw new Error("AIW_CODEX_NETWORK_ACCESS is forbidden for Workers");
  const repositoryRef = env("AIW_REPOSITORY_REF", "").trim();
  const repositoryRoot = env("AIW_REPOSITORY_ROOT", "").trim();
  if ((repositoryRef === "") !== (repositoryRoot === "")) throw new Error("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together");
  const capabilities = new Set(env("AIW_WORKER_CAPABILITIES", "coding").split(",").map(s => s.trim()).filter(Boolean));
  if (repositoryRef) capabilities.add(`repo:${repositoryRef}`);
  const fallbackWorkspace = env("AIW_WORKSPACE", repositoryRoot || process.cwd());
  return {
    baseUrl: env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080").replace(/\/$/, ""),
    workerId: env("AIW_WORKER_ID", "codex-typescript-1"), token: env("AIW_WORKER_TOKEN", ""),
    capabilities: [...capabilities].sort(), maxConcurrency: positiveInt("AIW_WORKER_MAX_CONCURRENCY", 1),
    fallbackWorkspace, repositoryRef, repositoryRoot,
    worktreeRoot: env("AIW_WORKTREE_ROOT", path.resolve(repositoryRoot || fallbackWorkspace, "..", ".aidev-worktrees")),
    pollMs: positiveInt("AIW_WORKER_POLL_MS", 1000), heartbeatMs: positiveInt("AIW_WORKER_HEARTBEAT_MS", 20000),
    model: process.env.AIW_CODEX_MODEL || undefined,
    sandboxMode: env("AIW_CODEX_SANDBOX", "workspace-write") as SandboxMode,
    approvalPolicy: env("AIW_CODEX_APPROVAL_POLICY", "never") as ApprovalMode,
    reasoningEffort: env("AIW_CODEX_REASONING_EFFORT", "high") as ModelReasoningEffort,
    networkAccess: false,
  };
}
function env(name: string, fallback: string): string { return process.env[name] ?? fallback; }
function positiveInt(name: string, fallback: number): number {
  const value = Number.parseInt(process.env[name] ?? String(fallback), 10);
  if (!Number.isFinite(value) || value < 1) throw new Error(`${name} must be >= 1`);
  return value;
}
function sleep(ms: number): Promise<void> { return new Promise(resolve => setTimeout(resolve, ms)); }
