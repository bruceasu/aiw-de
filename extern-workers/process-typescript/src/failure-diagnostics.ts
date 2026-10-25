import { RuntimeFault, sha256 } from "./runtime-core.js";
import { ProtocolError } from "../runtime-core/index.mjs";
import type { EvidenceRecorder } from "../runtime-core/index.mjs";

export interface ToolFailure {
  code: string;
  category: string;
}

export function workerDebugEnabled(): boolean {
  return /^(1|true|yes)$/i.test(process.env.AIW_WORKER_DEBUG?.trim() ?? "");
}

/** Best-effort credential masking; DEBUG output can still contain project text. */
function debugText(value: unknown, limit: number): { text: string | null; truncated: boolean } {
  if (typeof value !== "string") return { text: null, truncated: false };
  let text = value;
  for (const [name, secret] of Object.entries(process.env)) {
    if (/(?:KEY|TOKEN|PASSWORD|SECRET|CREDENTIAL|COOKIE|AUTH)/i.test(name) && secret && secret.length >= 4) {
      text = text.split(secret).join("[REDACTED]");
    }
  }
  text = text.replace(/\b(Bearer\s+)[^\s"']+/gi, "$1[REDACTED]")
    .replace(/(https?:\/\/)[^\s/@]+:[^\s/@]+@/gi, "$1[REDACTED]@")
    .replace(/((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret|authorization|cookie)["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s&,;]+)/gi, "$1[REDACTED]")
    .replace(/\bsk-[A-Za-z0-9_-]{8,}/g, "[REDACTED]");
  const truncated = text.length > limit;
  return { text: truncated ? text.slice(0, limit / 2) + "\n[TRUNCATED]\n" + text.slice(-limit / 2) : text, truncated };
}

function debugToolFailure(recorder: EvidenceRecorder, item: any, cwd: string): void {
  console.error(`[AIW DEBUG] ${JSON.stringify({ time: new Date().toISOString(), event: "tool-failure-detail",
    ...toolIdentity(item), evidence: recorder.directory,
    command: debugText(item?.command, 4096), cwd: debugText(item?.cwd ?? cwd, 1024),
    output: debugText(item?.aggregatedOutput ?? item?.aggregated_output, 8192),
    error: debugText(item?.error?.message, 2048) })}`);
}

/** Inspect bounded output in memory; retain only fixed codes, never command/source text. */
function classifyToolFailure(item: any): ToolFailure {
  const output = [item?.aggregatedOutput, item?.aggregated_output, item?.error?.message]
    .filter((value): value is string => typeof value === "string")
    .map(value => value.slice(0, 8192) + "\n" + value.slice(-8192)).join("\n");
  if (/fatal:\s*not a git repository/i.test(output)) {
    return { code: "GIT_REPOSITORY_UNAVAILABLE", category: "TOOL_ERROR" };
  }
  if (/bwrap:[^\n]*(?:No permissions to create a new namespace|Operation not permitted)|(?:unshare|create[^\n]*namespace)[^\n]*(?:Operation not permitted|Permission denied)/i.test(output)) {
    return { code: "SANDBOX_NAMESPACE_UNAVAILABLE", category: "CONFIGURATION" };
  }
  if (/command not found|is not recognized as (?:the name of|an internal or external)/i.test(output)) {
    return { code: "COMMAND_NOT_FOUND", category: "CONFIGURATION" };
  }
  if (/Permission denied|Access is denied/i.test(output)) {
    return { code: "COMMAND_PERMISSION_DENIED", category: "CONFIGURATION" };
  }
  if (/No such file or directory|cannot find the path specified/i.test(output)) {
    return { code: "COMMAND_PATH_NOT_FOUND", category: "TOOL_ERROR" };
  }
  if (/Could not resolve host|Failed to connect to|ECONNREFUSED|ENOTFOUND/i.test(output)) {
    return { code: "COMMAND_CONNECTION_FAILED", category: "TOOL_ERROR" };
  }
  return { code: "UNRESOLVED_TOOL_FAILURE", category: "OUTPUT_INVALID" };
}

function commandArgs(command: unknown, depth = 0): string[] | null {
  if (typeof command !== "string" || command.length > 16384 || depth > 1) return null;
  const args: string[] = [];
  let token = "", quote = "", started = false, escaped = false;
  for (const char of command) {
    if (escaped) { token += char; escaped = false; started = true; continue; }
    if (quote === "'") {
      if (char === "'") quote = ""; else token += char;
      continue;
    }
    if (char === "\\") { escaped = true; started = true; continue; }
    if (quote === '"') {
      if (char === '"') quote = "";
      else if (char === "$" || char === "`") return null;
      else token += char;
      continue;
    }
    if (char === "'" || char === '"') { quote = char; started = true; continue; }
    if ("|&;<>()`$\n\r".includes(char)) return null;
    if (/\s/.test(char)) {
      if (started) { args.push(token); token = ""; started = false; }
    } else { token += char; started = true; }
  }
  if (quote || escaped) return null;
  if (started) args.push(token);
  if (!args.length || args.length > 256) return null;
  const name = args[0].split("/").at(-1);
  if (["bash", "sh", "zsh"].includes(name || "") && args.length === 3 && ["-c", "-lc"].includes(args[1])) {
    return commandArgs(args[2], depth + 1);
  }
  return args;
}

function commandMetadata(item: any, cwd: string) {
  const args = commandArgs(item?.command);
  const name = args?.[0].split("/").at(-1);
  const search = name === "rg" || name === "grep";
  const operation = search ? (name === "rg" && args?.includes("--files") ? "list-files" : "search") : "command";
  const outputs = [item?.aggregatedOutput, item?.aggregated_output].filter((value): value is string => typeof value === "string");
  const known = outputs.length > 0;
  const outputBytes = known ? outputs.reduce((total, value) => total + Buffer.byteLength(value, "utf8"), 0) : undefined;
  const exitCode = item?.exitCode ?? item?.exit_code;
  const noMatches = search && exitCode === 1 && known && outputs.every(value => !value.trim())
    && item?.error == null && !args?.some(arg => /^--(?:pre|hostname-bin)(?:=|$)/.test(arg));
  const digest = typeof item?.command === "string" && item.command.length <= 16384
    ? sha256(JSON.stringify([typeof item.cwd === "string" ? item.cwd : cwd, item.command])) : undefined;
  return { operation, noMatches, digest, outputBytes, unknown: known ? undefined : "command-output-not-provided" };
}

function toolIdentity(item: any) {
  const callId = typeof item?.id === "string" && /^[A-Za-z0-9._-]{1,160}$/.test(item.id) ? item.id : "UNKNOWN";
  const tool = ["commandExecution", "command_execution", "fileChange", "file_change", "mcpToolCall"].includes(item?.type)
    ? item.type : "other";
  const candidate = item?.exitCode ?? item?.exit_code;
  return { callId, tool, exitCode: Number.isSafeInteger(candidate) ? candidate : null };
}

/** Tool failures are diagnostics, not final delivery gates. Matching retries only annotate history. */
export class ToolFailureTracker {
  private failures = new Map<string, { diagnosis: ToolFailure; callId: string }>();
  private recorder?: EvidenceRecorder;
  private debugFailures = 0;

  async observe(recorder: EvidenceRecorder, item: any, cwd: string): Promise<void> {
    this.recorder = recorder;
    const identity = toolIdentity(item);
    const command = ["commandExecution", "command_execution"].includes(item?.type);
    const details = commandMetadata(item, cwd);
    const failed = item?.status === "failed" || command && identity.exitCode !== null && identity.exitCode !== 0;
    const key = command ? details.digest : undefined;
    const previous = key ? this.failures.get(key) : undefined;
    if (command && details.noMatches) {
      await recorder.record("sdk-search-empty", { ...identity, operation: details.operation, digest: key,
        outputBytes: 0, status: "completed", code: "SEARCH_NO_MATCHES" });
      console.log(`${new Date().toISOString()} read probe: operation=${details.operation} call=${identity.callId} exit=1 code=SEARCH_NO_MATCHES`);
    } else if (failed) {
      const diagnosis = classifyToolFailure(item);
      await recorder.record("sdk-tool-failed", { ...identity, operation: details.operation, digest: key,
        outputBytes: details.outputBytes, unknown: details.unknown, code: diagnosis.code, category: diagnosis.category });
      console.error(`${new Date().toISOString()} tool failed: tool=${identity.tool} operation=${details.operation} call=${identity.callId} exit=${identity.exitCode ?? "unknown"} code=${diagnosis.code} outputKnown=${details.outputBytes !== undefined} evidence=${recorder.directory}`);
      if (workerDebugEnabled()) {
        if (this.debugFailures < 8) debugToolFailure(recorder, item, cwd);
        else if (this.debugFailures === 8) console.error("[AIW DEBUG] tool failure detail limit reached (8 per attempt)");
        this.debugFailures++;
      }
      if (key) {
        if (this.failures.size >= 1024 && !this.failures.has(key)) this.failures.delete(this.failures.keys().next().value!);
        this.failures.set(key, { diagnosis, callId: identity.callId });
      }
      return;
    }
    if (previous && command && (details.noMatches || !failed && identity.exitCode === 0)) {
      await recorder.record("sdk-tool-recovered", { ...identity, operation: details.operation, digest: key,
        parentCallId: previous.callId, status: "completed", code: "TOOL_FAILURE_RECOVERED" });
      this.failures.delete(key!);
      console.log(`${new Date().toISOString()} tool recovered: call=${identity.callId} previous=${previous.callId} operation=${details.operation}`);
    }
  }

  assertSuccessful(): void {
    // A healthy evidence trail is required; historical command errors do not decide task acceptance.
    this.recorder?.assertHealthy();
  }
}

/** Connection logs contain status/code only; raw HTTP or SDK errors can contain credentials. */
export function connectionFailureCode(error: unknown): string {
  if (error instanceof ProtocolError && Number.isInteger(error.status)) return `HTTP_${error.status}`;
  if (error instanceof RuntimeFault) return /^[A-Z][A-Z0-9_]{0,119}$/.test(error.code) ? error.code : "OPERATION_FAILED";
  const value = error as { code?: unknown; cause?: { code?: unknown } } | null;
  const code = value?.cause?.code ?? value?.code;
  const allowed = ["ECONNREFUSED", "ECONNRESET", "ENOTFOUND", "ETIMEDOUT", "EHOSTUNREACH", "ENETUNREACH", "UND_ERR_CONNECT_TIMEOUT", "CERT_HAS_EXPIRED"];
  return typeof code === "string" && allowed.includes(code) ? code : "OPERATION_FAILED";
}
