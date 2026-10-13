import { RuntimeFault, readSkillPackage, type Context } from "./runtime-core.js";
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

export function resolveTaskProfile(task: PromptTask, role: string): ProcessProfile {
  const profile = getProfile(role);
  if (task.taskType !== "EXTERNAL_WORK") throw new RuntimeFault("ROLE_TASK_TYPE_DENIED", "CONFIGURATION");
  if (typeof task.skillRef !== "string" || task.skillRef !== profile.primarySkill || !/^[a-z0-9][a-z0-9-]*$/.test(task.skillRef)) {
    throw new RuntimeFault("ROLE_SKILL_DENIED", "CONFIGURATION");
  }
  return Object.freeze({ ...profile, allowedTaskTypes: Object.freeze([...profile.allowedTaskTypes]),
    protocolSkills: Object.freeze([...profile.protocolSkills]) }) as unknown as ProcessProfile;
}

export function buildPrompt(task: PromptTask, role: string, skillsRoot: string, context?: Context, frozenProfile?: ProcessProfile): string {
  const profile = frozenProfile ?? resolveTaskProfile(task, role);
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
    "- Never run git push or publish a Git branch. Return the local result revision only.",
  ].join("\n");
  const skills = [...profile.protocolSkills, ...(profile.primarySkill === "engineering-artifact-protocol" ? [] : [profile.primarySkill])]
    .map(skill => readSkillPackage(skillsRoot, skill, context)).join("\n\n");
  const permission = profile.readOnly ? "READ-ONLY semantic/review task; do not modify repository files" : "WRITABLE repository task";
  return `AI Dev Manager R10 Process Worker\nProcess role: ${profile.name}\nPrimary R2 Skill: ${profile.primarySkill}\nProfile instruction: ${profile.instruction}\nPermission boundary: ${permission}\nTask ID: ${task.taskId}\nTask type: ${task.taskType}\nAttempt: ${task.attemptNo}${skillRef}\n\n=== Frozen R2 process context ===\n${boundary}\n\n${skills}\n\n=== Current authoritative work instruction ===\n${instruction}${inputs}\n\nExecute only this bounded task. Preserve the Control Plane authority boundary. For read-only roles, do not change repository files. For writable roles, work only inside the assigned workspace.`;
}

export function completionReport(resultKind: string, finalResponse: string): CompletionReport {
  const summary = finalResponse.trim();
  if (!summary) throw new RuntimeFault("COMPLETION_REPORT_EMPTY", "OUTPUT_INVALID");
  if (resultKind === "REPORT" || resultKind === "WORKSPACE") return { verdict: "PASS", summary };
  if (resultKind !== "VERDICT") throw new RuntimeFault("RESULT_KIND_INVALID", "CONFIGURATION");
  const marker = summary.split(/\r?\n/).at(-1)?.trim();
  const verdict = marker?.startsWith("AIW_VERDICT:") ? marker.slice("AIW_VERDICT:".length).trim() : null;
  if (verdict !== "PASS" && verdict !== "REPAIR_REQUIRED") {
    throw new RuntimeFault("COMPLETION_VERDICT_INVALID", "OUTPUT_INVALID");
  }
  return { verdict, summary };
}

export function sandboxForProfile(profile: ProcessProfile, configured: string): "read-only" | "workspace-write" | "danger-full-access" {
  if (profile.readOnly) return "read-only";
  if (configured === "read-only" || configured === "workspace-write" || configured === "danger-full-access") return configured;
  throw new Error(`unsupported AIW_CODEX_SANDBOX: ${configured}`);
}
