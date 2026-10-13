#!/usr/bin/env node
// Local POSIX test fleet. Each role is a separate, detached process.
import { spawn } from "node:child_process";
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const entry = join(root, "dist", "index.js");
const stateDir = join(root, ".ai", "worker-manager");
const roles = ["requirements", "architect", "planner", "coder", "integrator", "reviewer",
  "stage-reviewer", "verifier", "integration-verifier", "repairer"];
const runtimes = ["codex-cli", "codex-app-server", "openai-agents"];

function fail(message) { console.error(message); process.exit(2); }
function usage() {
  fail("Usage: node scripts/manage-workers.mjs start <role|all> <codex-cli|codex-app-server|openai-agents>\n"
    + "       node scripts/manage-workers.mjs stop [role|all]\n"
    + "       node scripts/manage-workers.mjs status [role|all]\n"
    + "       node scripts/manage-workers.mjs list\n"
    + "       node scripts/manage-workers.mjs logs <role>");
}
function statePath(role) { return join(stateDir, `${role}.json`); }
function logPath(role) { return join(stateDir, `${role}.log`); }
function state(role) {
  try { return JSON.parse(readFileSync(statePath(role), "utf8")); }
  catch (error) { if (error.code === "ENOENT") return null; throw error; }
}
function alive(record, role) {
  const pid = record?.pid;
  if (!Number.isSafeInteger(pid) || pid < 1) return false;
  try { process.kill(pid, 0); } catch (error) { return error.code === "EPERM"; }
  // Linux: refuse to signal a recycled PID, and treat zombies as stopped.
  if (process.platform === "linux") {
    try {
      const status = readFileSync(`/proc/${pid}/status`, "utf8");
      if (/^State:\s+Z/m.test(status)) return false;
      const command = readFileSync(`/proc/${pid}/cmdline`, "utf8").replaceAll("\0", " ");
      return command.includes(`manage-workers.mjs --worker ${role} ${record.runtime}`);
    } catch { return false; }
  }
  return true;
}
function selected(target) {
  if (target === "all") return roles;
  if (roles.includes(target)) return [target];
  usage();
}
function current(role) {
  const record = state(role);
  return record && alive(record, role) ? record : null;
}
function start(role, runtime) {
  const running = current(role);
  if (running) { console.log(`${role}: already running (PID ${running.pid})`); return; }
  if (!existsSync(entry)) fail("Missing dist/index.js; run npm run build first.");
  mkdirSync(stateDir, { recursive: true });
  const workerId = `${process.env.AIW_WORKER_ID_PREFIX?.trim() || "test-ts"}-${role}-1`;
  const capabilityKey = `AIW_WORKER_CAPABILITIES_${role.toUpperCase().replaceAll("-", "_")}`;
  const capabilities = process.env[capabilityKey] ?? "";
  const fd = openSync(logPath(role), "a", 0o600);
  let child;
  try {
    child = spawn(process.execPath, [fileURLToPath(import.meta.url), "--worker", role, runtime], {
      cwd: process.cwd(), detached: true, stdio: ["ignore", fd, fd],
      env: { ...process.env, AIW_PROCESS_ROLE: role, AIW_AGENT_RUNTIME: runtime,
        AIW_WORKER_ID: workerId, AIW_WORKER_CAPABILITIES: capabilities },
    });
    if (!child.pid) fail(`${role}: failed to spawn`);
    writeFileSync(statePath(role), JSON.stringify({ pid: child.pid, workerId, runtime,
      capabilities, startedAt: new Date().toISOString() }) + "\n", { mode: 0o600 });
    child.unref();
  } finally { closeSync(fd); }
  console.log(`${role}: started PID ${child.pid}, ID ${workerId}, log ${logPath(role)}`);
}
function stop(role) {
  const record = state(role);
  if (!record) { console.log(`${role}: stopped`); return; }
  if (!alive(record, role)) {
    rmSync(statePath(role), { force: true });
    console.log(`${role}: stopped (stale PID cleared)`);
    return;
  }
  try { process.kill(record.pid, "SIGTERM"); }
  catch (error) { if (error.code !== "ESRCH") throw error; }
  console.log(`${role}: stop requested for PID ${record.pid}; check status for drain completion`);
}
function status(role) {
  const record = state(role);
  const condition = record ? (alive(record, role) ? "RUNNING" : "STOPPED") : "STOPPED";
  console.log(`${role.padEnd(21)} ${condition.padEnd(8)} ${record?.pid ?? "-"}  ${record?.workerId ?? "-"}`);
}

const [command, target, runtime, extra] = process.argv.slice(2);
if (command === "--worker") {
  if (!roles.includes(target) || !runtimes.includes(runtime) || extra) usage();
  await import(entry);
} else if (command === "start") {
  if (!target || !runtimes.includes(runtime) || extra) usage();
  if (process.platform === "win32") fail("Background manager requires POSIX; use WSL on Windows.");
  if (process.env.AIW_AGENT_RUNTIME && process.env.AIW_AGENT_RUNTIME !== runtime) {
    fail("AIW_AGENT_RUNTIME conflicts with the selected runtime.");
  }
  if (runtime === "openai-agents" && !process.env.OPENAI_API_KEY?.trim()) {
    fail("OPENAI_API_KEY is required for openai-agents.");
  }
  for (const role of selected(target)) start(role, runtime);
} else if (command === "stop" || command === "status") {
  if (runtime) usage();
  for (const role of selected(target || "all")) (command === "stop" ? stop : status)(role);
} else if (command === "list") {
  if (target) usage();
  console.log("Available roles:");
  for (const role of roles) status(role);
} else if (command === "logs") {
  if (!roles.includes(target) || runtime) usage();
  console.log(logPath(target));
} else usage();
