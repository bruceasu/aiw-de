export interface AiProfile {
  provider: string;
  runtime: string;
  model: string;
  effort: string;
  fast: boolean;
  level: number;
}

export interface AiGuidance {
  version: string;
  allowedProfiles: Array<{ id: string; profile: AiProfile }>;
  previous: { profileId: string; failureCategory: string | null; state: string } | null;
  suggestedProfile: string;
}

/** The Worker chooses within the approved ordered path; the Control Plane validates the choice. */
export function chooseAiProfile(guidance: AiGuidance, runtime: "codex-cli" | "codex-app-server" | "openai-agents" = "codex-cli"): { id: string; profile: AiProfile; reason: string } {
  const allowed = guidance.allowedProfiles.filter(item => item.profile.provider === "openai" && item.profile.runtime === runtime
    && (runtime !== "openai-agents" || (!item.profile.fast && ["none", "minimal", "low", "medium", "high"].includes(item.profile.effort))));
  if (!allowed.length) throw new Error(runtime !== "openai-agents"
    ? "no approved system Codex profile matching the selected mode"
    : "no compatible approved OpenAI Agents profile (use fast=false and supported effort)");
  const previousIndex = allowed.findIndex(item => item.id === guidance.previous?.profileId);
  const upgrade = guidance.previous?.failureCategory === "MODEL_ERROR" || guidance.previous?.failureCategory === "OUTPUT_INVALID";
  const index = previousIndex < 0 ? 0 : upgrade ? Math.min(previousIndex + 1, allowed.length - 1) : previousIndex;
  const selected = allowed[index]!;
  const reason = previousIndex < 0 ? "initial approved profile"
    : upgrade ? `model failure; move from ${guidance.previous!.profileId} to ${selected.id}`
    : `retain ${selected.id} after ${guidance.previous?.failureCategory ?? guidance.previous?.state}`;
  return { ...selected, reason };
}

export interface AiPreferences { model?: string | null; effort?: string | null }
const preferenceValue = (value: unknown): value is string => typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,159}$/.test(value);

/** Only model/effort rejections qualify. Code, tools, transport and permission failures do not. */
export function isAiPreferenceRejection(error: unknown): boolean {
  const value = error as { code?: unknown; message?: unknown; error?: { code?: unknown; message?: unknown; param?: unknown }; param?: unknown };
  const code = String(value?.error?.code ?? value?.code ?? "");
  if (["model_not_found", "unsupported_model", "unsupported_reasoning_effort", "AI_PREFERENCES_REJECTED"].includes(code)) return true;
  const param = String(value?.error?.param ?? value?.param ?? "");
  const message = String(value?.error?.message ?? value?.message ?? "");
  return /^(model|reasoning[._]effort|reasoning_effort|effort)$/.test(param)
    && /unsupported|not supported|invalid|not found|does not exist|not available/i.test(message)
    || /(?:model|reasoning[_ .]effort|effort).{0,100}(?:not supported|unsupported|not found|does not exist|not available)|(?:unsupported|invalid).{0,30}(?:model|reasoning[_ .]effort)/i.test(message);
}

export function chooseStepAiProfile(guidance: AiGuidance, runtime: "codex-cli" | "codex-app-server" | "openai-agents", preferences?: AiPreferences | null) {
  const fallback = chooseAiProfile(guidance, runtime);
  if (!preferences || (!preferences.model && !preferences.effort)) return fallback;
  const compatible = guidance.allowedProfiles.filter(item => item.profile.provider === "openai" && item.profile.runtime === runtime
    && (!preferences.model || item.profile.model === preferences.model)
    && (!preferences.effort || item.profile.effort === preferences.effort)
    && (runtime !== "openai-agents" || (!item.profile.fast && ["none", "minimal", "low", "medium", "high"].includes(item.profile.effort))));
  return compatible.length ? { ...compatible[0]!, reason: "Step preference matches an approved profile" } : fallback;
}

/** One retry only, after a confirmed parameter rejection before tools have run. */
export async function withAiPreferences<T>(execution: import("./agent-runtime.js").AgentRunRequest["execution"],
  runtime: "codex-cli" | "codex-app-server" | "openai-agents", defaults: { model: string; effort: string },
  run: (selected: typeof execution) => Promise<T>): Promise<T> {
  const preferences = execution.task.aiPreferences as AiPreferences | undefined;
  const baseline = execution.task.aiGuidance ? chooseAiProfile(execution.task.aiGuidance, runtime).profile : undefined;
  let preferred = execution.selection as AiProfile | undefined;
  let reason = "Worker choice";
  if (preferences?.model || preferences?.effort) {
    if (execution.task.aiGuidance) {
      preferred = chooseStepAiProfile(execution.task.aiGuidance, runtime, preferences).profile;
      reason = (!preferences.model || preferred.model === preferences.model) && (!preferences.effort || preferred.effort === preferences.effort)
        ? "Step preference" : "Step preference unavailable in approved profiles; use Worker choice";
    } else {
      const validModel = !preferences.model || preferenceValue(preferences.model);
      const efforts = runtime === "openai-agents" ? ["none", "minimal", "low", "medium", "high"] : ["none", "minimal", "low", "medium", "high", "xhigh"];
      const validEffort = !preferences.effort || efforts.includes(preferences.effort);
      preferred = { provider: "openai", runtime, level: 1, fast: false,
        model: validModel && preferences.model ? preferences.model : defaults.model,
        effort: validEffort && preferences.effort ? preferences.effort : defaults.effort };
      reason = validModel && validEffort ? "Step preference" : "Invalid Step preference; use Worker configuration for invalid fields";
    }
  }
  await execution.attempt.recorder.record("ai-settings", { model: preferred?.model ?? defaults.model, effort: preferred?.effort ?? defaults.effort, reason });
  try { return await run({ ...execution, selection: preferred }); }
  catch (error) {
    if (execution.attempt.signal.aborted || !preferences || !preferred || (error as { code?: string })?.code !== "AI_PREFERENCES_REJECTED"
      || (preferred.model === (baseline?.model ?? defaults.model) && preferred.effort === (baseline?.effort ?? defaults.effort))) throw error;
    await execution.attempt.recorder.record("ai-settings-fallback", { reason: "Step model or effort rejected before tool execution; use Worker choice", model: baseline?.model ?? defaults.model, effort: baseline?.effort ?? defaults.effort });
    return await run({ ...execution, selection: baseline });
  }
}
