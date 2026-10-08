import { RuntimeFault } from "./errors.mjs";
import path from "node:path";
export function positiveSetting(name, fallback) {
  const value = Number(process.env[name] ?? fallback);
  if (!Number.isSafeInteger(value) || value < 1) throw new RuntimeFault(`INVALID_${name}`, "CONFIGURATION");
  return value;
}
export function runtimeSettings() {
  return {
    taskTimeoutMs: positiveSetting("AIW_WORKER_ATTEMPT_TIMEOUT_MS", process.env.AIW_AGENT_TASK_TIMEOUT_MS ?? 900000),
    cleanupTimeoutMs: positiveSetting("AIW_WORKER_CLEANUP_TIMEOUT_MS", 10000),
    gitTimeoutMs: positiveSetting("AIW_WORKER_GIT_TIMEOUT_MS", 120000),
    maxInputBytes: positiveSetting("AIW_WORKER_MAX_INPUT_BYTES", 1024 * 1024),
    evidenceMaxBytes: positiveSetting("AIW_WORKER_EVIDENCE_MAX_BYTES", 4 * 1024 * 1024),
    evidenceRoot: process.env.AIW_WORKER_EVIDENCE_ROOT ? path.resolve(process.env.AIW_WORKER_EVIDENCE_ROOT) : undefined,
  };
}
