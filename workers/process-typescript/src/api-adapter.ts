import { existsSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import type { WorkerBackend, WorkerConfig } from "./index.js";
import type { AgentRuntime, AgentRunRequest } from "./agent-runtime.js";
import { completed } from "./agent-runtime.js";
import { ApiRuntime, type ApiRuntimeConfig } from "./api-runtime.js";
import { buildPrompt, completionReport, resolveTaskProfile } from "./instructions.js";
import { chooseStepAiProfile, withAiPreferences } from "./ai-profiles.js";
import { RuntimeFault, positiveSetting, sha256 } from "./runtime-core.js";

class AgentsRuntime implements AgentRuntime {
  readonly type = "openai-agents" as const;
  readonly capabilities = { resume: false, cancelById: false, attemptCancellation: true, eventStreaming: true, tools: true } as const;
  private readonly runtime: ApiRuntime;

  constructor(cfg: ApiRuntimeConfig) { this.runtime = new ApiRuntime(cfg); }

  async run({ execution, emit }: AgentRunRequest) {
    await emit({ type: "session-started", sessionId: `api:${execution.task.attemptId}` });
    const output = await this.runtime.execute({ ...execution, onEvent: name => {
      void emit({ type: "runtime-event", name });
    } });
    return completed(output);
  }
  settled(timeoutMs?: number) { return this.runtime.settled(timeoutMs); }
  assertSuccessful() { this.runtime.assertSuccessful(); }
  clear() { this.runtime.clear(); }
}

function installedVersion(name: string): string {
  let directory = path.dirname(createRequire(import.meta.url).resolve(name));
  for (let depth = 0; depth < 4; depth++, directory = path.dirname(directory)) {
    const file = path.join(directory, "package.json");
    if (!existsSync(file)) continue;
    const metadata = JSON.parse(readFileSync(file, "utf8"));
    if (metadata.name === name && typeof metadata.version === "string") return metadata.version;
  }
  throw new Error("API_SDK_IDENTITY_UNKNOWN");
}

function apiConfig(cfg: WorkerConfig): ApiRuntimeConfig {
  if (/^(1|true|yes)$/i.test(process.env.AIW_AGENT_ALLOW_SHELL ?? "false")) throw new Error("AIW_AGENT_ALLOW_SHELL is forbidden for process Workers");
  const apiKey = (process.env.OPENAI_API_KEY ?? "").trim();
  if (!apiKey) throw new Error("OPENAI_API_KEY is required before registering the worker");
  const reasoningEffort = (process.env.AIW_OPENAI_REASONING_EFFORT ?? "").trim();
  if (reasoningEffort && !["minimal", "low", "medium", "high"].includes(reasoningEffort)) throw new Error("unsupported AIW_OPENAI_REASONING_EFFORT");
  const compactHistory = /^(1|true|yes)$/i.test(process.env.AIW_AGENT_COMPACT_HISTORY ?? "false");
  if (compactHistory && (!process.env.AIW_AGENT_COMPACT_AFTER_BYTES || !process.env.AIW_AGENT_SUMMARY_MAX_TOKENS)) {
    throw new Error("history compaction requires explicit input and summary budgets");
  }
  const maxOutputChars = positiveSetting("AIW_AGENT_MAX_OUTPUT_CHARS", 24000);
  if (maxOutputChars < 512) throw new Error("AIW_AGENT_MAX_OUTPUT_CHARS must allow at least 512 UTF-8 bytes");
  return {
    apiKey, baseURL: process.env.OPENAI_BASE_URL || undefined, model: process.env.AIW_OPENAI_MODEL ?? "gpt-4.1", reasoningEffort,
    maxTurns: positiveSetting("AIW_AGENT_MAX_TURNS", 40), apiTimeoutMs: positiveSetting("AIW_OPENAI_TIMEOUT_MS", 120000),
    taskTimeoutMs: cfg.taskTimeoutMs, toolTimeoutMs: positiveSetting("AIW_AGENT_TOOL_TIMEOUT_MS", 60000),
    maxOutputChars, maxInputBytes: cfg.maxInputBytes,
    allowShell: false, compactHistory,
    compactAfterBytes: compactHistory ? positiveSetting("AIW_AGENT_COMPACT_AFTER_BYTES", 0) : undefined,
    summaryMaxTokens: compactHistory ? positiveSetting("AIW_AGENT_SUMMARY_MAX_TOKENS", 0) : undefined,
    aiwToolCatalog: process.env.AIW_AGENT_AIW_TOOL_CATALOG || undefined,
  };
}

export async function createApiBackend(cfg: WorkerConfig): Promise<WorkerBackend> {
  const versions = { agents: installedVersion("@openai/agents"), core: installedVersion("@openai/agents-core"), provider: installedVersion("@openai/agents-openai"), openai: installedVersion("openai"), zod: installedVersion("zod") };
  if (versions.agents !== "0.20.0" || versions.core !== "0.20.0" || versions.provider !== "0.20.0" || versions.openai !== "7.32.0" || versions.zod !== "4.6.7") throw new Error("API_SDK_BASELINE_MISMATCH");
  const settings = apiConfig(cfg);
  const runtimes = new Map<number, AgentsRuntime>();
  return {
    startup: `using OpenAI Agents SDK model=${settings.model}; shell=${settings.allowShell}`,
    createAdapter: (task, slot) => {
      let runtime = runtimes.get(slot);
      if (!runtime) { runtime = new AgentsRuntime(settings); runtimes.set(slot, runtime); }
      const engine = runtime;
      let frozenProfile: ReturnType<typeof resolveTaskProfile>;
      return {
        runtime: engine.type, versions,
        select: task => task.aiGuidance ? chooseStepAiProfile(task.aiGuidance, "openai-agents", task.aiPreferences) : null,
        profile: task => (frozenProfile = resolveTaskProfile(task, cfg.role)),
        prompt: (task, context) => buildPrompt(task, cfg.role, cfg.skillsRoot, context, frozenProfile),
        execute: async execution => (await withAiPreferences(execution, "openai-agents", { model: settings.model, effort: settings.reasoningEffort }, selected => engine.run({ execution: selected, emit: event => {
          if (event.type === "session-started") execution.onSession(event.sessionId);
          else console.log(`slot ${slot}: task=${task.taskId} ${event.name}`);
        } }))).output,
        report: completionReport, digest: sha256,
        settled: timeoutMs => engine.settled(timeoutMs),
        assertSuccessful: () => engine.assertSuccessful(),
        clear: () => engine.clear(),
      };
    },
  };
}
