import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as toml from "@iarna/toml";

export const ROLE_FILES = {
  requirements: "requirement-analyst",
  architect: "solution-architect",
  planner: "implementation-planner",
  coder: "coding-executor",
  integrator: "integration-executor",
  reviewer: "code-reviewer",
  verifier: "verifier",
  "integration-verifier": "integration-verifier",
  repairer: "repair-executor",
  "release-readiness": "release-readiness-manager",
  orchestrator: "software-development-orchestrator",
} as const;

export type Role = keyof typeof ROLE_FILES;
export interface ProcessProfile {
  name: string;
  workerIdPrefix: string;
  roleCapability: string;
  allowedTaskTypes: string[];
  readOnly: boolean;
  instruction: string;
  protocolSkills: string[];
  primarySkill: string;
}

const fields = ["name", "workerIdPrefix", "roleCapability", "allowedTaskTypes", "readOnly", "instruction", "protocolSkills", "primarySkill"];
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
  if (typeof value.readOnly !== "boolean") throw new Error(`invalid profile ${file}: readOnly must be a boolean`);
  return value as unknown as ProcessProfile;
}

export function getProfile(role: string, taskType?: string): ProcessProfile {
  const key = role.trim().toLowerCase();
  if (!(key in ROLE_FILES)) throw new Error(`unsupported AIW_PROCESS_ROLE=${role}; choose one of: ${Object.keys(ROLE_FILES).join(", ")}`);
  const resolved = key === "verifier" && taskType === "INTEGRATION_VERIFICATION" ? "integration-verifier" : key;
  return loadProfile(path.join(profileDirectory, `${ROLE_FILES[resolved as Role]}.toml`));
}

export function assertExecutableRole(role: string): void {
  if (role === "release-readiness" || role === "orchestrator") {
    throw new Error(`role ${role} is preserved from R2 but R9 has no matching executable TaskType`);
  }
}
