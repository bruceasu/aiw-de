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
      { id: "fast", profile: { provider: "openai", runtime: "codex-sdk", model: "gpt-6-luna", effort: "medium", fast: false, level: 1 } },
      { id: "normal", profile: { provider: "openai", runtime: "codex-sdk", model: "gpt-6-sol", effort: "medium", fast: false, level: 2 } },
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
  assert.deepEqual([...covered].sort(), [
    "CODE_REVIEW", "IMPLEMENTATION", "IMPLEMENTATION_PLANNING", "INTEGRATION",
    "INTEGRATION_VERIFICATION", "REPAIR", "REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "VERIFICATION",
  ]);
  for (const role of ["requirements", "architect", "planner", "reviewer", "verifier", "integration-verifier"]) {
    assert.equal(sandboxForProfile(getProfile(role), "workspace-write"), "read-only");
  }
  for (const role of ["coder", "integrator", "repairer"]) {
    assert.equal(sandboxForProfile(getProfile(role), "workspace-write"), "workspace-write");
  }
  assert.throws(() => assertExecutableRole("release-readiness"), /no matching executable TaskType/);
  assert.throws(() => assertExecutableRole("orchestrator"), /no matching executable TaskType/);
});

test("verifier selects integration instructions per task without changing the launch role", () => {
  assert.equal(getProfile("verifier").roleCapability, "verification");
  assert.equal(getProfile("verifier", "INTEGRATION_VERIFICATION").roleCapability, "integration-verification");
  const prompt = buildPrompt({
    taskId: "integration-check", taskType: "INTEGRATION_VERIFICATION", attemptNo: 2,
    instruction: "Check the integrated revision.", inputs: [{ taskId: "coding-1", resultRevision: "abc" }],
  }, "verifier", skillsRoot);
  assert.match(prompt, /integration scope/);
  assert.match(prompt, /engineering-artifact-protocol\/SKILL.md/);
  assert.match(prompt, /software-test-verifier\/SKILL.md/);
  assert.match(prompt, /Upstream task inputs \(authoritative\)/);
  assert.match(prompt, /Check the integrated revision/);
  assert.ok(prompt.indexOf("engineering-artifact-protocol/SKILL.md") < prompt.indexOf("software-test-verifier/SKILL.md"));
  assert.throws(() => buildPrompt({ taskId: "wrong", taskType: "IMPLEMENTATION", attemptNo: 1 }, "verifier", skillsRoot), /cannot execute task type/);
});

test("completion reports require evidence and a final review verdict", () => {
  assert.deepEqual(completionReport("REQUIREMENT_ANALYSIS", "  summary  "), { verdict: "PASS", summary: "summary" });
  assert.throws(() => completionReport("SOLUTION_ARCHITECTURE", "  "), /non-empty final response/);
  assert.deepEqual(completionReport("CODE_REVIEW", "Finding\nAIW_VERDICT: REPAIR_REQUIRED"), {
    verdict: "REPAIR_REQUIRED", summary: "Finding\nAIW_VERDICT: REPAIR_REQUIRED",
  });
  assert.throws(() => completionReport("CODE_REVIEW", "AIW_VERDICT: PASS\nextra text"), /must end with/);
  assert.equal(completionReport("IMPLEMENTATION", "done"), null);
});
