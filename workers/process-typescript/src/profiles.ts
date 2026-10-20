import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as toml from "@iarna/toml";

export const ROLE_FILES = {
  "decision-delegate": "decision-delegate",
  secretary: "secretary",
  "plan-coordinator": "plan-coordinator",
  "goal-discussion": "goal-discussion",
  requirements: "requirements",
  architect: "architect",
  planner: "planner",
  coder: "coder",
  integrator: "integrator",
  reviewer: "reviewer",
  "stage-reviewer": "stage-reviewer",
  "recovery-decider": "recovery-decider",
  verifier: "verifier",
  "integration-verifier": "integration-verifier",
  repairer: "repairer",
} as const;

const ADVISORY_ROLES = new Set(["release-readiness", "orchestrator"]);

export type Role = keyof typeof ROLE_FILES;
export interface ProcessProfile {
  name: string;
  workerIdPrefix: string;
  roleCapability: string;
  allowedTaskTypes: string[];
  resultKind: "REPORT" | "VERDICT" | "WORKSPACE";
  readOnly: boolean;
  instruction: string;
  protocolSkills: string[];
  primarySkill: string;
}

const fields = ["name", "workerIdPrefix", "roleCapability", "allowedTaskTypes", "resultKind", "readOnly", "instruction", "protocolSkills", "primarySkill"];
const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const profileDirectory = path.resolve(process.env.AIW_PROCESS_PROFILES_ROOT || (
  existsSync(path.join(moduleDirectory, "profiles"))
    ? path.join(moduleDirectory, "profiles")
    : path.join(moduleDirectory, "../profiles")
));

export function loadProfile(file: string): ProcessProfile {
  if (path.extname(file) !== ".toml") throw new Error(`profile must be a .toml file: ${file}`);
  const value = toml.parse(readFileSync(file, "utf8")) as Record<string, unknown>;
  const actual = Object.keys(value).sort();
  if (actual.join("|") !== [...fields].sort().join("|")) throw new Error(`invalid profile ${file}: fields=${actual.join(",")}`);
  for (const key of ["name", "workerIdPrefix", "roleCapability", "instruction", "primarySkill"]) {
    if (typeof value[key] !== "string" || !(value[key] as string).trim()) throw new Error(`invalid profile ${file}: ${key} must be a nonempty string`);
  }
  for (const key of ["allowedTaskTypes", "protocolSkills"]) {
    const items = value[key];
    if (!Array.isArray(items) || items.length === 0 || items.some(x => typeof x !== "string" || !x.trim())) {
      throw new Error(`invalid profile ${file}: ${key} must be a nonempty string array`);
    }
  }
  if (value.allowedTaskTypes instanceof Array && (value.allowedTaskTypes.length !== 1 || value.allowedTaskTypes[0] !== "EXTERNAL_WORK")) {
    throw new Error(`invalid profile ${file}: allowedTaskTypes must be [EXTERNAL_WORK]`);
  }
  if (!["REPORT", "VERDICT", "WORKSPACE"].includes(String(value.resultKind))) {
    throw new Error(`invalid profile ${file}: resultKind must be REPORT, VERDICT or WORKSPACE`);
  }
  if ((value.resultKind === "WORKSPACE") !== (value.readOnly === false)) {
    throw new Error(`invalid profile ${file}: resultKind conflicts with readOnly`);
  }
  if (typeof value.readOnly !== "boolean") throw new Error(`invalid profile ${file}: readOnly must be a boolean`);
  return value as unknown as ProcessProfile;
}

export function getProfile(role: string, taskType?: string): ProcessProfile {
  const key = role.trim().toLowerCase();
  if (!(key in ROLE_FILES)) throw new Error(`unsupported AIW_PROCESS_ROLE=${role}; choose one of: ${Object.keys(ROLE_FILES).join(", ")}`);
  return loadProfile(path.join(profileDirectory, `${key}.toml`));
}

export function assertExecutableRole(role: string): void {
  if (ADVISORY_ROLES.has(role)) {
    throw new Error(`role ${role} is a non-executable advisory role`);
  }
}

export const ROLE_CAPABILITIES = new Set(["secretary-chat", "plan-coordination", "goal-discussion", "requirement-analysis", "solution-architecture", "implementation-planning", "coding", "integration", "repair", "code-review", "verification", "integration-verification", "workflow-review", "workflow-recovery-decision", "release-readiness", "development-orchestration"]);
export function assertRoleCapabilities(capabilities: Iterable<string>, _profile: ProcessProfile): void {
  for (const capability of capabilities) {
    if (capability === "*" || capability === "delegated-decision" || capability === "flexible-work-v1" || capability.startsWith("repo:") || ROLE_CAPABILITIES.has(capability)) {
      throw new Error("ROLE_CAPABILITY_DENIED");
    }
  }
}
