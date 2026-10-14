import assert from "node:assert/strict";
import { test } from "node:test";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildPrompt, completionReport, sandboxForProfile } from "./instructions.js";
import { assertExecutableRole, getProfile, ROLE_FILES } from "./profiles.js";
import { chooseAiProfile, type AiGuidance } from "./ai-profiles.js";
import { ToolFailureTracker } from "./failure-diagnostics.js";
import { ToolHost, RuntimeFault } from "./runtime-core.js";
import { AttemptRunner } from "../runtime-core/index.mjs";
import type { EvidenceRecorder } from "../runtime-core/index.mjs";
import { matchesCodexSandbox } from "./app-server-adapter.js";

test("App Server direct host access accepts its response without a networkAccess field", () => {
  const settings = { sandbox: "danger-full-access" as const, network: true };
  assert.equal(matchesCodexSandbox({ type: "dangerFullAccess" }, settings), true);
  assert.equal(matchesCodexSandbox({ type: "workspaceWrite", networkAccess: true }, settings), false);
  assert.equal(matchesCodexSandbox({ type: "readOnly", networkAccess: true }, settings), false);
  assert.equal(matchesCodexSandbox(undefined, settings), false);
  assert.equal(matchesCodexSandbox({ type: "readOnly", networkAccess: false }, { sandbox: "read-only", network: false }), true);
  assert.equal(matchesCodexSandbox({ type: "workspaceWrite", networkAccess: false }, { sandbox: "workspace-write", network: true }), false);
  assert.equal(matchesCodexSandbox({ type: "workspaceWrite" }, { sandbox: "workspace-write", network: false }), false);
});

test("an elapsed-time warning lets the active attempt finish without cancellation", async () => {
  const events: string[] = [];
  let warn!: () => void;
  const warning = new Promise<void>(resolve => { warn = resolve; });
  const recorder = { directory: "/evidence", record: async (event: string) => { events.push(event); },
    assertHealthy: () => {}, finish: async () => {} };
  const attempt = new AttemptRunner({ warningMs: 1, onWarning: warn, recorder });
  try {
    const result = await attempt.step("execute", async () => { await warning; return "finished"; });
    assert.equal(result, "finished");
    assert.equal(attempt.signal.aborted, false);
    assert.equal(await attempt.settled(), true);
    assert.ok(events.includes("attempt-duration-warning"));
  } finally { attempt.dispose(); }
});

test("an explicitly configured hard deadline still cancels the active attempt", async () => {
  const attempt = new AttemptRunner({ timeoutMs: 1 });
  try {
    await new Promise<void>(resolve => attempt.signal.addEventListener("abort", () => resolve(), { once: true }));
    assert.equal(attempt.signal.aborted, true);
    assert.match(String(attempt.signal.reason), /ATTEMPT_DEADLINE_EXCEEDED/);
  } finally { attempt.dispose(); }
});

test("a different successful command does not leave a historical failure as a completion gate", async () => {
  const events: string[] = [];
  let healthy = true;
  const recorder = { directory: "/evidence", record: async (event: string) => { events.push(event); },
    assertHealthy: () => { if (!healthy) throw new RuntimeFault("CRITICAL_EVIDENCE_FAILED"); } } as unknown as EvidenceRecorder;
  const tracker = new ToolFailureTracker();
  await tracker.observe(recorder, { id: "install", type: "commandExecution", command: "npm install",
    exitCode: 1, status: "completed", aggregatedOutput: "npm error EAI_AGAIN" }, "/workspace");
  await tracker.observe(recorder, { id: "verify", type: "commandExecution", command: "npm ci --offline",
    exitCode: 0, status: "completed", aggregatedOutput: "installed" }, "/workspace");
  assert.doesNotThrow(() => tracker.assertSuccessful());
  assert.ok(events.includes("sdk-tool-failed"));
  assert.throws(() => completionReport("WORKSPACE", ""), /COMPLETION_REPORT_EMPTY/);
  healthy = false;
  assert.throws(() => tracker.assertSuccessful(), /CRITICAL_EVIDENCE_FAILED/);
});

test("Agents tool errors remain recorded without requiring identical arguments before completion", async () => {
  const events: Array<{ event: string; status?: string }> = [];
  const cancellation = new AbortController();
  const host = new ToolHost({ cwd: "/workspace", readOnly: false, allowShell: false, signal: cancellation.signal,
    recorder: { directory: "/evidence", finish: async () => {},
      record: async (event: string, details: { status?: string }) => { events.push({ event, status: details.status }); },
      assertHealthy: () => {} },
    coordinator: { run: async <T>(_request: unknown, _signal: unknown, operation: () => Promise<T>): Promise<T> => operation(),
      settled: async () => {} } });
  const tool = { name: "probe", run: async (args: { valid: boolean }) =>
    ({ content: args.valid ? "ready" : "missing", status: args.valid ? "ok" : "error" }) };
  await host.execute(tool, { valid: false });
  await host.execute(tool, { valid: true });
  assert.doesNotThrow(() => host.assertSuccessful());
  assert.ok(events.some(event => event.event === "tool-terminal" && event.status === "error"));
  cancellation.abort(new RuntimeFault("ATTEMPT_CANCELLED"));
  assert.throws(() => host.assertSuccessful(), /ATTEMPT_CANCELLED/);
});

test("Worker upgrades only after a model failure and stays inside approved profiles", () => {
  const guidance: AiGuidance = {
    version: "v1", suggestedProfile: "normal",
    allowedProfiles: [
      { id: "fast", profile: { provider: "openai", runtime: "codex-cli", model: "gpt-6-luna", effort: "medium", fast: false, level: 1 } },
      { id: "normal", profile: { provider: "openai", runtime: "codex-cli", model: "gpt-6-sol", effort: "medium", fast: false, level: 2 } },
      { id: "other", profile: { provider: "openai", runtime: "agents-sdk", model: "gpt-6-astra", effort: "high", fast: false, level: 3 } },
    ],
    previous: { profileId: "fast", failureCategory: "MODEL_ERROR", state: "FAILED" },
  };
  assert.equal(chooseAiProfile(guidance).id, "normal");
  assert.equal(chooseAiProfile({ ...guidance, previous: { ...guidance.previous!, failureCategory: "RATE_LIMIT" } }).id, "fast");
  assert.equal(chooseAiProfile({ ...guidance, previous: null }).id, "fast");
  assert.equal(chooseAiProfile({ ...guidance, previous: { profileId: "normal", failureCategory: "MODEL_ERROR", state: "FAILED" } }).id, "normal");
});

const skillsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../skills");

test("active profiles cover the current task types and enforce repository permissions", () => {
  const active = Object.keys(ROLE_FILES).filter(role => !["release-readiness", "orchestrator"].includes(role));
  const covered = new Set(active.flatMap(role => getProfile(role).allowedTaskTypes));
  assert.deepEqual([...covered].sort(), ["EXTERNAL_WORK"]);
  for (const role of ["requirements", "architect", "planner", "reviewer", "verifier", "integration-verifier"]) {
    assert.equal(sandboxForProfile(getProfile(role), "workspace-write"), "read-only");
  }
  for (const role of ["coder", "integrator", "repairer"]) {
    assert.equal(sandboxForProfile(getProfile(role), "workspace-write"), "workspace-write");
  }
  assert.throws(() => assertExecutableRole("release-readiness"), /non-executable advisory role/);
  assert.throws(() => assertExecutableRole("orchestrator"), /non-executable advisory role/);
});

test("verifier remains fixed and requires the exact approved skill", () => {
  assert.equal(getProfile("verifier", "INTEGRATION_VERIFICATION").roleCapability, "verification");
  assert.equal(getProfile("integration-verifier").roleCapability, "integration-verification");
  const prompt = buildPrompt({ taskId: "check", taskType: "EXTERNAL_WORK", skillRef: "software-test-verifier", attemptNo: 1,
    instruction: "Check the exact upstream revision.", inputs: [{ resultRevision: "a".repeat(40) }] }, "verifier", skillsRoot);
  assert.match(prompt, /software-test-verifier\/SKILL.md/);
  assert.match(prompt, /engineering-artifact-protocol\/SKILL.md/);
  assert.throws(() => buildPrompt({ taskId: "wrong", taskType: "IMPLEMENTATION", attemptNo: 1 }, "verifier", skillsRoot), /ROLE_TASK_TYPE_DENIED/);
  assert.throws(() => buildPrompt({ taskId: "wrong", taskType: "EXTERNAL_WORK", skillRef: "..\/coding-work-package-executor", attemptNo: 1 }, "verifier", skillsRoot), /ROLE_SKILL_DENIED/);
});

test("completion reports require evidence and a final review verdict", () => {
  assert.deepEqual(completionReport("REPORT", "  summary  "), { verdict: "PASS", summary: "summary" });
  assert.throws(() => completionReport("REPORT", "  "), /COMPLETION_REPORT_EMPTY/);
  assert.deepEqual(completionReport("VERDICT", "Finding\nAIW_VERDICT: REPAIR_REQUIRED"), {
    verdict: "REPAIR_REQUIRED", summary: "Finding\nAIW_VERDICT: REPAIR_REQUIRED",
  });
  assert.throws(() => completionReport("VERDICT", "AIW_VERDICT: PASS\nextra text"), /COMPLETION_VERDICT_INVALID/);
  assert.deepEqual(completionReport("WORKSPACE", "done"), { verdict: "PASS", summary: "done" });
});
