import assert from "node:assert/strict";
import { test } from "node:test";
import { chooseStepAiProfile, isAiPreferenceRejection, withAiPreferences, type AiGuidance } from "./ai-profiles.js";
import { RuntimeFault } from "./runtime-core.js";
import type { AttemptExecution } from "../runtime-core/index.mjs";

const defaults = { model: "worker-model", effort: "medium" };
function execution(preferences?: unknown, guidance?: AiGuidance) {
  const events: Array<{ event: string; details: any }> = [];
  const input = { task: { aiPreferences: preferences, aiGuidance: guidance },
    attempt: { signal: new AbortController().signal, recorder: { record: async (event: string, details: any) => { events.push({ event, details }); } } } } as unknown as AttemptExecution;
  return { input, events };
}

test("empty Step preferences preserve Worker choice; individual fields inherit Worker defaults", async () => {
  const empty = execution();
  assert.equal(await withAiPreferences(empty.input, "codex-cli", defaults, async run => run.selection), undefined);
  for (const [preferences, expected] of [
    [{ model: "step-model" }, { model: "step-model", effort: "medium" }],
    [{ effort: "high" }, { model: "worker-model", effort: "high" }],
    [{ model: "bad\nmodel", effort: "unknown" }, defaults],
  ] as const) {
    const { input } = execution(preferences);
    const result = await withAiPreferences(input, "codex-cli", defaults, async run => run.selection);
    assert.equal(result.model, expected.model);
    assert.equal(result.effort, expected.effort);
  }
});

test("a rejected Step model falls back once; code failures and cancellation never retry", async () => {
  const { input, events } = execution({ model: "unavailable-model" });
  const selections: unknown[] = [];
  const output = await withAiPreferences(input, "codex-cli", defaults, async run => {
    selections.push(run.selection);
    if (selections.length === 1) throw new RuntimeFault("AI_PREFERENCES_REJECTED", "MODEL_ERROR");
    return "completed";
  });
  assert.equal(output, "completed");
  assert.equal(selections.length, 2);
  assert.equal(selections[1], undefined);
  assert.ok(events.some(item => item.event === "ai-settings-fallback"));
  for (const fault of ["CODEX_EXEC_NOT_COMPLETED", "TOOL_ERROR", "AI_PREFERENCES_REJECTED"]) {
    let calls = 0;
    await assert.rejects(withAiPreferences(input, "codex-cli", defaults, async () => {
      calls++; throw new RuntimeFault(fault);
    }));
    assert.equal(calls, fault === "AI_PREFERENCES_REJECTED" ? 2 : 1);
  }
  const controller = new AbortController(); controller.abort();
  input.attempt = { ...input.attempt, signal: controller.signal } as typeof input.attempt;
  let calls = 0;
  await assert.rejects(withAiPreferences(input, "codex-cli", defaults, async () => { calls++; throw new RuntimeFault("AI_PREFERENCES_REJECTED"); }));
  assert.equal(calls, 1);
});

test("Step preferences stay within approved model policy and fall back to its Worker path", async () => {
  const profile = { provider: "openai", runtime: "codex-cli", model: "worker-model", effort: "medium", fast: false, level: 1 };
  const guidance: AiGuidance = { version: "v1", suggestedProfile: "worker", previous: null,
    allowedProfiles: [{ id: "worker", profile }, { id: "step", profile: { ...profile, model: "step-model", effort: "high" } }] };
  assert.equal(chooseStepAiProfile(guidance, "codex-cli", { model: "step-model" }).id, "step");
  assert.equal(chooseStepAiProfile(guidance, "codex-cli", { model: "unapproved" }).id, "worker");
  const { input } = execution({ model: "step-model" }, guidance);
  let calls = 0;
  const selected = await withAiPreferences(input, "codex-cli", defaults, async run => {
    if (++calls === 1) throw new RuntimeFault("AI_PREFERENCES_REJECTED");
    return run.selection;
  });
  assert.equal(selected.model, "worker-model");
  assert.equal(selected.effort, "medium");
  assert.equal(isAiPreferenceRejection({ code: "model_not_found" }), true);
  assert.equal(isAiPreferenceRejection(new Error("command exited with status 1")), false);
});
