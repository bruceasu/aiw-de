import { readSkillPackage, type Context } from "./runtime-core.js";
import { getProfile, type ProcessProfile } from "./profiles.js";

export interface PromptTask {
  taskId: string;
  taskType: string;
  subjectType?: string;
  subjectId?: string;
  attemptNo: number;
  skillRef?: string | null;
  instruction?: string | null;
  inputs?: unknown[];
}

export interface CompletionReport {
  verdict: "PASS" | "REPAIR_REQUIRED";
  summary: string;
}

export function buildPrompt(task: PromptTask, role: string, skillsRoot: string, context?: Context): string {
  const profile = getProfile(role, task.taskType);
  if (!profile.allowedTaskTypes.includes(task.taskType)) {
    throw new Error(`profile ${profile.name} cannot execute task type ${task.taskType}; allowed=${profile.allowedTaskTypes.join(",")}`);
  }
  const instruction = task.instruction?.trim() || `Execute AI Dev Manager task ${task.taskId} for ${task.subjectType} ${task.subjectId}.`;
  const inputs = task.inputs?.length ? `\n\nUpstream task inputs (authoritative):\n${JSON.stringify(task.inputs, null, 2)}` : "";
  const skillRef = task.skillRef?.trim() ? `\nControl Plane skill reference: ${task.skillRef.trim()}` : "";
  const boundary = [
    "R2 PROCESS AUTHORITY BOUNDARY:",
    "- The Control Plane owns lifecycle state, scheduling, durable IDs, approval and authoritative commits.",
    "- This worker owns bounded semantic execution only.",
    "- Never create a second workflow engine or claim lifecycle authority.",
    "- Treat upstream inputs supplied by the Control Plane as authoritative.",
  ].join("\n");
  const skills = [...profile.protocolSkills, ...(profile.primarySkill === "engineering-artifact-protocol" ? [] : [profile.primarySkill])]
    .map(skill => readSkillPackage(skillsRoot, skill, context)).join("\n\n");
  const permission = profile.readOnly ? "READ-ONLY semantic/review task; do not modify repository files" : "WRITABLE repository task";
  return `AI Dev Manager R10 Process Worker\nProcess role: ${profile.name}\nPrimary R2 Skill: ${profile.primarySkill}\nProfile instruction: ${profile.instruction}\nPermission boundary: ${permission}\nTask ID: ${task.taskId}\nTask type: ${task.taskType}\nAttempt: ${task.attemptNo}${skillRef}\n\n=== Frozen R2 process context ===\n${boundary}\n\n${skills}\n\n=== Current authoritative work instruction ===\n${instruction}${inputs}\n\nExecute only this bounded task. Preserve the Control Plane authority boundary. For read-only roles, do not change repository files. For writable roles, work only inside the assigned workspace.`;
}

export function completionReport(taskType: string, finalResponse: string): CompletionReport | null {
  if (["REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING"].includes(taskType)) {
    const summary = finalResponse.trim();
    if (!summary) throw new Error(`${taskType} must return a non-empty final response`);
    return { verdict: "PASS", summary };
  }
  if (!["CODE_REVIEW", "VERIFICATION", "INTEGRATION_VERIFICATION"].includes(taskType)) return null;
  const lines = finalResponse.trimEnd().split(/\r?\n/);
  const marker = lines.at(-1)?.trim();
  const verdict = marker?.startsWith("AIW_VERDICT:") ? marker.slice("AIW_VERDICT:".length).trim() : null;
  if (verdict !== "PASS" && verdict !== "REPAIR_REQUIRED") {
    throw new Error("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED");
  }
  return { verdict, summary: finalResponse.trim() };
}

export function sandboxForProfile(profile: ProcessProfile, configured: string): "read-only" | "workspace-write" | "danger-full-access" {
  if (profile.readOnly) return "read-only";
  if (configured === "read-only" || configured === "workspace-write" || configured === "danger-full-access") return configured;
  throw new Error(`unsupported AIW_CODEX_SANDBOX: ${configured}`);
}
