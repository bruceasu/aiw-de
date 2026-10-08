import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { assertExecutableRole, getProfile } from "./profiles.js";
import { ControlPlaneClient, runWorkerSlot, runtimeSettings, positiveSetting } from "./runtime-core.js";
import type { AttemptAdapter, RuntimeSettings } from "../runtime-core/index.mjs";

type RuntimeType = "codex-sdk" | "openai-agents";

export interface WorkerConfig extends RuntimeSettings {
  runtime: RuntimeType;
  role: string;
  skillsRoot: string;
  baseUrl: string;
  workerId: string;
  token: string;
  capabilities: string[];
  maxConcurrency: number;
  fallbackWorkspace: string;
  repositoryRef: string;
  repositoryRoot: string;
  worktreeRoot: string;
  gitPublishRemote: string;
  pollMs: number;
  heartbeatMs: number;
  httpTimeoutMs: number;
}

export interface WorkerBackend {
  startup: string;
  createAdapter(task: any, slot: number): AttemptAdapter;
}

function selectedRuntime(): RuntimeType {
  const selected = (process.env.AIW_AGENT_RUNTIME ?? "").trim();
  if (selected !== "codex-sdk" && selected !== "openai-agents") {
    throw new Error("AIW_AGENT_RUNTIME must explicitly be codex-sdk or openai-agents");
  }
  return selected;
}

function env(name: string, fallback: string): string { return process.env[name] ?? fallback; }

function loadConfig(runtime: RuntimeType): WorkerConfig {
  const role = env("AIW_PROCESS_ROLE", "requirements").trim().toLowerCase();
  assertExecutableRole(role);
  const profile = getProfile(role);
  const repositoryRef = env("AIW_REPOSITORY_REF", "").trim();
  const repositoryRoot = env("AIW_REPOSITORY_ROOT", "").trim();
  if ((repositoryRef === "") !== (repositoryRoot === "")) throw new Error("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together");
  const capabilities = new Set(env("AIW_WORKER_CAPABILITIES", "").split(",").map(s => s.trim()).filter(Boolean));
  capabilities.add(profile.roleCapability);
  if (role === "verifier") capabilities.add(getProfile("integration-verifier").roleCapability);
  if (repositoryRef) capabilities.add(`repo:${repositoryRef}`);
  const fallbackWorkspace = path.resolve(env("AIW_WORKSPACE", repositoryRoot || process.cwd()));
  const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
  const bundledSkills = path.join(moduleDirectory, "skills");
  const skillsRoot = path.resolve(env("AIW_PROCESS_SKILLS_ROOT", existsSync(bundledSkills) ? bundledSkills : path.join(moduleDirectory, "../skills")));
  return {
    ...runtimeSettings(), runtime, role, skillsRoot,
    baseUrl: env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080").replace(/\/$/, ""),
    workerId: env("AIW_WORKER_ID", runtime === "codex-sdk" ? `process-${role}-1` : `process-api-${role}-1`),
    token: env("AIW_WORKER_TOKEN", ""),
    capabilities: [...capabilities].sort(), maxConcurrency: positiveSetting("AIW_WORKER_MAX_CONCURRENCY", 1),
    fallbackWorkspace, repositoryRef, repositoryRoot: repositoryRoot ? path.resolve(repositoryRoot) : "",
    worktreeRoot: path.resolve(env("AIW_WORKTREE_ROOT", path.resolve(repositoryRoot || fallbackWorkspace, "..", ".aidev-worktrees"))),
    gitPublishRemote: env("AIW_GIT_PUBLISH_REMOTE", ""),
    pollMs: positiveSetting("AIW_WORKER_POLL_MS", 1000),
    heartbeatMs: positiveSetting("AIW_WORKER_HEARTBEAT_MS", 20000),
    httpTimeoutMs: positiveSetting("AIW_WORKER_HTTP_TIMEOUT_MS", 15000),
  };
}

const runtime = selectedRuntime();
const cfg = loadConfig(runtime);
// Agents reads logging controls during import; set them before loading its adapter.
if (runtime === "openai-agents") {
  process.env.OPENAI_AGENTS_DONT_LOG_MODEL_DATA = "1";
  process.env.OPENAI_AGENTS_DONT_LOG_TOOL_DATA = "1";
  process.env.OPENAI_AGENTS_DISABLE_TRACING = "1";
  process.env.DEBUG = "";
}
const backend: WorkerBackend = runtime === "codex-sdk"
  ? await (await import("./codex-adapter.js")).createCodexBackend(cfg)
  : await (await import("./api-adapter.js")).createApiBackend(cfg);
const force = new AbortController();
cfg.forceSignal = force.signal;
const client = new ControlPlaneClient(cfg, `${runtime}:${cfg.role}`);
let stopping = false;
for (const sig of ["SIGINT", "SIGTERM"] as const) process.on(sig, () => {
  if (stopping) force.abort();
  stopping = true;
  void client.drain().catch(() => console.error("worker drain failed"));
});
console.log(backend.startup);
await client.register();
const workerHeartbeat = setInterval(() => void client.workerHeartbeat().catch(() => console.error("worker heartbeat failed")), cfg.heartbeatMs);
try {
  await Promise.all(Array.from({ length: cfg.maxConcurrency }, (_, i) => {
    const slot = i + 1;
    return runWorkerSlot({
      cfg, client, shouldStop: () => stopping,
      createAdapter: task => backend.createAdapter(task, slot),
      onOutcome: (outcome, task) => console.log(`slot ${slot}: task=${task.taskId} status=${outcome.status}`),
      onError: () => console.error(`slot ${slot}: polling failed`),
      onUncertain: async () => { stopping = true; force.abort(); await client.drain(); },
    });
  }));
} finally {
  clearInterval(workerHeartbeat);
  try { await client.drain(); } catch { console.error("worker drain failed"); }
  try { await client.offline(); } catch { console.error("worker offline failed"); }
}
