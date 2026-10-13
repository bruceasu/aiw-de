import assert from "node:assert/strict";
import { test } from "node:test";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildPrompt, completionReport, sandboxForProfile } from "./instructions.js";
import { assertExecutableRole, getProfile, ROLE_FILES } from "./profiles.js";
import { chooseAiProfile, type AiGuidance } from "./ai-profiles.js";

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
