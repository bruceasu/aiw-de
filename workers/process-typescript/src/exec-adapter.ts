import type { WorkerBackend, WorkerConfig } from "./index.js";
import type { AgentRuntime, AgentRunRequest } from "./agent-runtime.js";
import { completed } from "./agent-runtime.js";
import { CodexProcess, codexSettings, codexCacheConfig, assertCredentialBoundary, CODEX_EXECUTION_ARGS, type CodexSettings } from "./codex-process.js";
import { codexBackend } from "./codex-adapter-common.js";
import { getProfile } from "./profiles.js";
import { RuntimeFault } from "./runtime-core.js";
import { isAiPreferenceRejection } from "./ai-profiles.js";
import { ToolFailureTracker } from "./failure-diagnostics.js";

class ExecRuntime implements AgentRuntime {
  readonly type = "codex-cli" as const;
  readonly capabilities = { resume: false, cancelById: false, attemptCancellation: true, eventStreaming: true, tools: true } as const;
  private process?: CodexProcess;
  private confirmed = true;
  constructor(private readonly settings: CodexSettings, private readonly cfg: WorkerConfig) {}
  async run({ execution, emit }: AgentRunRequest) {
    const { workspace, profile, attempt, selection } = execution;
    attempt.signal.throwIfAborted();
    assertCredentialBoundary(this.settings, workspace.cwd);
    attempt.signal.throwIfAborted();
    const sandbox = this.settings.sandbox;
    const effort = selection?.effort || this.settings.effort;
    const args = ["exec", "--ephemeral", "--json", "--sandbox", sandbox, "--cd", workspace.cwd, ...CODEX_EXECUTION_ARGS,
      "--config", 'approval_policy="never"', "--config", `model_reasoning_effort=${JSON.stringify(effort)}`,
      "--config", `sandbox_workspace_write.network_access=${this.settings.network}`,
      "--config", `sandbox_read_only.network_access=${this.settings.network}`,
      "--config", `shell_environment_policy.set=${codexCacheConfig(this.settings.env)}`,
      "--config", "sandbox_workspace_write.exclude_slash_tmp=true", "--config", "sandbox_workspace_write.exclude_tmpdir_env_var=true"];
    if (selection?.model || this.settings.model) args.push("--model", selection?.model || this.settings.model);
    if (selection && execution.task.aiGuidance) args.push("--config", `service_tier="${selection.fast ? "fast" : "default"}"`);
    args.push("-");
    let terminal = false, failed = false, output = "", sessionId = "", preferenceRejected = false, workStarted = false;
    const toolFailures = new ToolFailureTracker();
    this.confirmed = false;
    this.process = new CodexProcess(this.settings, args, workspace.cwd, async event => {
      if ((event.type === "item.started" || event.type === "item.completed")
        && !["reasoning"].includes(event.item?.type)) workStarted = true;
      if (event.type === "thread.started") {
        if (typeof event.thread_id !== "string" || !event.thread_id) throw new RuntimeFault("CODEX_SESSION_INVALID");
        if (sessionId || terminal) throw new RuntimeFault("CODEX_SESSION_REUSED");
        sessionId = event.thread_id;
        await emit({ type: "session-started", sessionId });
      } else if (event.type === "item.completed") {
        if (!sessionId || terminal) throw new RuntimeFault("CODEX_EVENT_OUT_OF_ORDER");
        const item = event.item;
        if (item?.type === "agent_message") {
          output = item.text;
          if (typeof output !== "string" || Buffer.byteLength(output, "utf8") > this.settings.maxBytes) throw new RuntimeFault("CODEX_OUTPUT_INVALID", "OUTPUT_INVALID");
        } else {
          await toolFailures.observe(attempt.recorder, item, workspace.cwd);
          await attempt.recorder.record("sdk-item-terminal", { callId: item?.id, tool: item?.type, status: item?.status || "completed" });
        }
      } else if (event.type === "turn.completed") {
        if (!sessionId || terminal) throw new RuntimeFault("CODEX_DUPLICATE_TERMINAL");
        terminal = true;
        const usage = event.usage;
        const known = Number.isSafeInteger(usage?.input_tokens) && Number.isSafeInteger(usage?.output_tokens);
        await attempt.recorder.record("model-terminal", { status: "completed", usageKnown: known, usageSource: "exec-json",
          ...(known ? { inputTokens: usage.input_tokens, outputTokens: usage.output_tokens, cachedTokens: usage.cached_input_tokens ?? 0 } : {}) });
      } else if (["turn.failed", "error"].includes(event.type)) {
        failed = true;
        preferenceRejected ||= isAiPreferenceRejection(event.error ?? event);
      }
    });
    const process = this.process;
    const abort = () => { void process.terminate(this.cfg.cleanupTimeoutMs).then(stopped => { this.confirmed = stopped; }); };
    attempt.signal.addEventListener("abort", abort, { once: true });
    try {
      if (attempt.signal.aborted) abort();
      await process.input(execution.prompt);
      // The attempt signal owns optional hard cancellation; elapsed-time warnings do not end Exec.
      const code = await process.finish(0);
      this.confirmed = await process.close(this.cfg.cleanupTimeoutMs);
      if (!this.confirmed) throw new RuntimeFault("PROCESS_TREE_UNCERTAIN");
      attempt.signal.throwIfAborted();
      if (!workStarted && (preferenceRejected || process.aiPreferenceRejected)) throw new RuntimeFault("AI_PREFERENCES_REJECTED", "MODEL_ERROR");
      if (code !== 0 || failed || !terminal || !output.trim()) throw new RuntimeFault("CODEX_EXEC_NOT_COMPLETED", "MODEL_ERROR");
      toolFailures.assertSuccessful();
      return completed(output);
    } catch (error) {
      if (!this.confirmed) this.confirmed = await process.terminate(this.cfg.cleanupTimeoutMs);
      if (!this.confirmed) throw new RuntimeFault("PROCESS_TREE_UNCERTAIN");
      throw error;
    } finally { attempt.signal.removeEventListener("abort", abort); }
  }
  async settled(timeoutMs = this.cfg.cleanupTimeoutMs) {
    if (!this.confirmed && this.process) this.confirmed = await this.process.terminate(timeoutMs);
    return this.confirmed;
  }
}

export async function createCodexExecBackend(cfg: WorkerConfig): Promise<WorkerBackend> {
  const settings = await codexSettings(getProfile(cfg.role).readOnly);
  return codexBackend(cfg, "codex-cli", settings, () => new ExecRuntime(settings, cfg));
}
