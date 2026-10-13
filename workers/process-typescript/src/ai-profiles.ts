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
