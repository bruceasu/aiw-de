import { existsSync } from "node:fs";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const roles = ["goal-discussion", "requirements", "architect", "planner", "coder", "integrator",
  "reviewer", "stage-reviewer", "recovery-decider", "verifier", "integration-verifier", "repairer"];
const prefixes = { "codex-cli": "process-exec", "codex-app-server": "process", "openai-agents": "process-api" };
const runtime = process.argv[2];
const entry = fileURLToPath(new URL("./dist/index.js", import.meta.url));

function fail(message) {
  console.error(message);
  process.exit(2);
}

if (process.argv.length !== 3 || !Object.hasOwn(prefixes, runtime)) {
  fail("Usage: node start-all.mjs <codex-cli|codex-app-server|openai-agents>");
}
if (process.env.AIW_AGENT_RUNTIME && process.env.AIW_AGENT_RUNTIME !== runtime) {
  fail("AIW_AGENT_RUNTIME conflicts with the runtime argument.");
}
if (process.env.AIW_WORKER_ID) {
  fail("Unset AIW_WORKER_ID for a fleet; use AIW_WORKER_ID_PREFIX for unique role IDs instead.");
}
if (!existsSync(entry)) fail("Missing dist/index.js. Run node build.mjs first.");
if (runtime === "openai-agents" && !process.env.OPENAI_API_KEY?.trim()) {
  fail("OPENAI_API_KEY is required for openai-agents.");
}

const prefix = process.env.AIW_WORKER_ID_PREFIX?.trim() || prefixes[runtime];
const children = new Set();
let stopping = false;

function stop(signal = "SIGTERM") {
  stopping = true;
  for (const child of children) child.kill(signal);
}
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => stop(signal));
}

await Promise.all(roles.map(role => new Promise(resolve => {
  if (stopping) { resolve(); return; }
  const workerId = `${prefix}-${role}-1`;
  const roleCapabilities = process.env[`AIW_WORKER_CAPABILITIES_${role.toUpperCase().replaceAll('-', '_')}`] ?? "";
  console.log(`Starting ${role}: runtime=${runtime}, workerId=${workerId}`);
  const child = spawn(process.execPath, [entry], {
    // Preserve the caller's cwd, matching run-role's workspace fallback.
    stdio: "inherit",
    // Give POSIX workers separate process groups so terminal Ctrl+C reaches
    // only the launcher, which forwards each signal once.
    detached: process.platform !== "win32",
    env: { ...process.env, AIW_AGENT_RUNTIME: runtime, AIW_PROCESS_ROLE: role,
      AIW_WORKER_ID: workerId, AIW_WORKER_CAPABILITIES: roleCapabilities },
  });
  children.add(child);
  child.on("error", error => {
    console.error(`${role}: ${error.message}`);
    process.exitCode = 1;
    stop();
  });
  child.on("close", (code, signal) => {
    children.delete(child);
    if (!stopping) {
      console.error(`${role} exited unexpectedly (${signal || code}); stopping the fleet.`);
      process.exitCode = code || 1;
      stop();
    }
    resolve();
  });
})));
