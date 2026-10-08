import { Codex } from "@openai/codex-sdk";
import type { ApprovalMode, ModelReasoningEffort, SandboxMode } from "@openai/codex-sdk";
import { readFileSync } from "node:fs";
import { lstat, readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import type { WorkerBackend, WorkerConfig } from "./index.js";
import type { AgentRuntime, AgentRunRequest } from "./agent-runtime.js";
import { completed } from "./agent-runtime.js";
import { buildPrompt, completionReport, sandboxForProfile } from "./instructions.js";
import { getProfile } from "./profiles.js";
import { chooseAiProfile } from "./ai-profiles.js";
import { resolveInstalledCodex } from "./codex-runtime.js";
import { RuntimeFault, sha256 } from "./runtime-core.js";

interface CodexConfig {
  model?: string;
  codexPath: string;
  codexEnv: Record<string, string>;
  sandboxMode: SandboxMode;
  approvalPolicy: ApprovalMode;
  reasoningEffort: ModelReasoningEffort;
  networkAccess: boolean;
}

class CodexRuntime implements AgentRuntime {
  readonly type = "codex-sdk" as const;
  readonly capabilities = { resume: false, cancelById: false, attemptCancellation: true, eventStreaming: true, tools: true } as const;
  private readonly codex: Codex;

  constructor(private readonly cfg: WorkerConfig & CodexConfig) {
    this.codex = new Codex({ codexPathOverride: cfg.codexPath, env: cfg.codexEnv });
  }

  async run({ execution, emit }: AgentRunRequest) {
    const { workspace, profile, prompt, context, attempt, selection } = execution;
    const rules: string[] = [];
    for (const name of ["AGENTS.md", "CODEX.md"]) {
      try {
        const file = path.join(workspace.cwd, name);
        const stat = await lstat(file);
        if (!stat.isFile() || stat.isSymbolicLink() || stat.size > this.cfg.maxInputBytes) throw new RuntimeFault("REPOSITORY_INSTRUCTION_UNSAFE", "CONFIGURATION");
        rules.push(`--- ${name} ---\n${context.source(file, await readFile(file, { encoding: "utf8", signal: attempt.signal }), "repository-rule")}`);
      } catch (error) { if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error; }
    }
    const boundedPrompt = context.assemble([...rules, prompt]);
    await context.record(boundedPrompt);
    await attempt.recorder.record("runtime-capabilities", { capability: "opaque-sdk-tool-loop", processTree: "native-codex-cli", unknown: "inner-tool-policies-model-window-automatic-sdk-context" });
    const selected = selection ? new Codex({ codexPathOverride: this.cfg.codexPath, env: this.cfg.codexEnv, config: { service_tier: selection.fast ? "fast" : "default" } }) : this.codex;
    const response = await workspace.coordinator.run({ mode: "X" }, attempt.signal, async () => {
      try {
        const thread = selected.startThread({
          workingDirectory: workspace.cwd,
          sandboxMode: sandboxForProfile(profile, this.cfg.sandboxMode) as SandboxMode,
          approvalPolicy: this.cfg.approvalPolicy,
          modelReasoningEffort: (selection?.effort ?? this.cfg.reasoningEffort) as ModelReasoningEffort,
          networkAccessEnabled: this.cfg.networkAccess,
          ...((selection?.model ?? this.cfg.model) ? { model: selection?.model ?? this.cfg.model } : {}),
        });
        const streamed = await thread.runStreamed(boundedPrompt, { signal: attempt.signal });
        let output = "";
        let finished = false;
        let failedTool = false;
        for await (const event of streamed.events) {
          attempt.signal.throwIfAborted();
          if (event.type === "thread.started") await emit({ type: "session-started", sessionId: event.thread_id });
          else if (event.type === "item.completed") {
            if (event.item.type === "agent_message") output = event.item.text;
            else {
              const item = event.item as { id: string; type: string; status?: string; exit_code?: number | null };
              const failed = item.status === "failed" || (item.type === "command_execution" && item.exit_code !== null && item.exit_code !== undefined && item.exit_code !== 0);
              failedTool ||= failed;
              await attempt.recorder.record("sdk-item-terminal", { callId: item.id, tool: item.type, status: failed ? "error" : item.status ?? "unknown", unknown: "inner-call-authorization-and-resource-locks" });
            }
          } else if (event.type === "turn.failed" || event.type === "error") throw new RuntimeFault("CODEX_TURN_FAILED", "MODEL_ERROR");
          else if (event.type === "turn.completed") {
            finished = true;
            await attempt.recorder.record("model-terminal", { status: "completed", usageKnown: true, usageSource: "codex-sdk-turn",
              inputTokens: event.usage.input_tokens, outputTokens: event.usage.output_tokens, cachedTokens: event.usage.cached_input_tokens });
          }
        }
        if (!finished || !output.trim()) throw new RuntimeFault("CODEX_TERMINAL_MISSING", "OUTPUT_INVALID");
        if (failedTool) throw new RuntimeFault("SDK_TOOL_FAILURE_REQUIRES_RECONCILIATION", "OUTPUT_INVALID");
        return output;
      } catch (error) {
        if (attempt.signal.aborted) attempt.resourceUncertain = true;
        throw error;
      }
    });
    return completed(response);
  }
}

export async function createCodexBackend(cfg: WorkerConfig): Promise<WorkerBackend> {
  const profile = getProfile(cfg.role);
  const sandboxMode = process.env.AIW_CODEX_SANDBOX ?? (profile.readOnly ? "read-only" : "workspace-write");
  if (profile.readOnly && sandboxMode !== "read-only") throw new Error("read-only process profile requires AIW_CODEX_SANDBOX=read-only");
  const codexRuntime = resolveInstalledCodex(process.env.AIW_CODEX_PATH);
  const sdkEntry = createRequire(import.meta.url).resolve("@openai/codex-sdk");
  const sdkVersion = JSON.parse(readFileSync(path.resolve(path.dirname(sdkEntry), "../package.json"), "utf8")).version;
  if (sdkVersion !== "0.155.1") throw new RuntimeFault("CODEX_SDK_BASELINE_MISMATCH", "CONFIGURATION");
  const settings: WorkerConfig & CodexConfig = {
    ...cfg,
    model: process.env.AIW_CODEX_MODEL || undefined,
    codexPath: codexRuntime.executablePath,
    codexEnv: Object.fromEntries(Object.entries(codexRuntime.env).filter(([key]) => !/^AIW_/i.test(key))),
    sandboxMode: sandboxMode as SandboxMode,
    approvalPolicy: (process.env.AIW_CODEX_APPROVAL_POLICY ?? "never") as ApprovalMode,
    reasoningEffort: (process.env.AIW_CODEX_REASONING_EFFORT ?? "high") as ModelReasoningEffort,
    networkAccess: /^(1|true|yes)$/i.test(process.env.AIW_CODEX_NETWORK_ACCESS ?? "false"),
  };
  const runtimes = new Map<number, CodexRuntime>();
  return {
    startup: `using installed Codex CLI: ${settings.codexPath}`,
    createAdapter: (_task, slot) => {
      let runtime = runtimes.get(slot);
      if (!runtime) { runtime = new CodexRuntime(settings); runtimes.set(slot, runtime); }
      const engine = runtime;
      return ({
      runtime: engine.type, versions: { codexSdk: sdkVersion },
      select: task => task.aiGuidance ? chooseAiProfile(task.aiGuidance, "codex-sdk") : null,
      profile: task => {
        const selected = getProfile(cfg.role, task.taskType);
        if (!selected.allowedTaskTypes.includes(task.taskType)) throw new RuntimeFault("ROLE_TASK_TYPE_DENIED", "CONFIGURATION");
        return selected;
      },
      prompt: (task, context) => buildPrompt(task, cfg.role, cfg.skillsRoot, context),
      execute: async execution => (await engine.run({ execution, emit: event => {
        if (event.type === "session-started") execution.onSession(event.sessionId);
      } })).output,
      report: completionReport, digest: sha256,
      });
    },
  };
}
