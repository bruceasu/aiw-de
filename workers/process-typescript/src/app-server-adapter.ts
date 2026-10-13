import type { WorkerBackend, WorkerConfig } from "./index.js";
import type { AgentRuntime, AgentRunRequest } from "./agent-runtime.js";
import { completed } from "./agent-runtime.js";
import { AppServerTransport } from "./app-server-transport.js";
import { codexBackend } from "./codex-adapter-common.js";
import { codexSettings, assertCredentialBoundary, type CodexSettings } from "./codex-process.js";
import { getProfile } from "./profiles.js";
import { RuntimeFault } from "./runtime-core.js";

class AppServerRuntime implements AgentRuntime {
  readonly type = "codex-app-server" as const;
  readonly capabilities = { resume: false, cancelById: false, attemptCancellation: true, eventStreaming: true, tools: true } as const;
  private confirmed = true;
  private toolFailed = false;
  constructor(private readonly transport: AppServerTransport, private readonly settings: CodexSettings, private readonly cfg: WorkerConfig) {}
  async run({ execution, emit }: AgentRunRequest) {
    const { attempt, workspace, profile, selection } = execution;
    const signal = attempt.signal;
    assertCredentialBoundary(this.settings, workspace.cwd);
    await this.transport.account(signal);
    const sandbox = profile.readOnly ? "read-only" : this.settings.sandbox;
    const started = await this.transport.request("thread/start", { cwd: workspace.cwd, ephemeral: true,
      approvalPolicy: "never", sandbox, config: { "sandbox_workspace_write.network_access": this.settings.network,
        "sandbox_read_only.network_access": this.settings.network, "sandbox_workspace_write.exclude_slash_tmp": true, "sandbox_workspace_write.exclude_tmpdir_env_var": true }, ...(selection?.model || this.settings.model ? { model: selection?.model || this.settings.model } : {}) }, signal);
    const threadId = started?.thread?.id;
    if (typeof threadId !== "string" || !threadId || started.thread.ephemeral !== true) throw new RuntimeFault("CODEX_EPHEMERAL_THREAD_UNCONFIRMED", "CONFIGURATION");
    if (started.thread.cliVersion !== "0.162.1" || started.cwd !== workspace.cwd || started.approvalPolicy !== "never"
      || started.sandbox?.type !== (profile.readOnly ? "readOnly" : sandbox === "read-only" ? "readOnly" : "workspaceWrite")
      || Boolean(started.sandbox?.networkAccess) !== this.settings.network) throw new RuntimeFault("CODEX_THREAD_POLICY_MISMATCH", "CONFIGURATION");
    const metadata = await this.transport.request("thread/read", { threadId, includeTurns: false }, signal);
    if (metadata?.thread?.id !== threadId || metadata.thread.ephemeral !== true) throw new RuntimeFault("CODEX_EPHEMERAL_THREAD_UNCONFIRMED", "CONFIGURATION");
    const events = this.transport.subscribe(threadId);
    let turnId: string | undefined;
    const messages = new Map<string, { text: string; phase: string | null }>();
    let completedBytes = 0, streamedBytes = 0;
    const message = (item: any) => {
      if (typeof item.id !== "string" || !item.id || typeof item.text !== "string"
        || (item.phase != null && !["commentary", "final_answer"].includes(item.phase))) throw new RuntimeFault("CODEX_MESSAGE_INVALID", "OUTPUT_INVALID");
      const previous = messages.get(item.id);
      if (previous) {
        if (previous.text !== item.text || previous.phase !== (item.phase ?? null)) throw new RuntimeFault("CODEX_MESSAGE_CONFLICT", "OUTPUT_INVALID");
        return;
      }
      completedBytes += Buffer.byteLength(item.text, "utf8");
      if (completedBytes > this.settings.maxBytes || messages.size >= 512) throw new RuntimeFault("CODEX_OUTPUT_TOO_LARGE", "OUTPUT_INVALID");
      messages.set(item.id, { text: item.text, phase: item.phase ?? null });
    };
    const finalOutput = () => {
      const values = [...messages.values()];
      return (values.filter(item => item.phase === "final_answer").at(-1) ?? values.filter(item => item.phase === null).at(-1))?.text ?? "";
    };
    const consume = async (event: any): Promise<string | null> => {
      const params = event.params;
      if (params?.threadId !== threadId) return null;
      if (event.method === "turn/completed") {
        if (params.turn?.id !== turnId) return null;
        if (!["completed", "failed", "interrupted"].includes(params.turn.status)) throw new RuntimeFault("CODEX_TERMINAL_UNKNOWN");
        this.confirmed = true;
        for (const item of params.turn.items ?? []) if (item.type === "agentMessage") message(item);
        await attempt.recorder.record("model-terminal", { status: params.turn.status, usageKnown: false, usageSource: "app-server-turn", unknown: "token-usage-may-be-notified-separately" });
        return params.turn.status;
      }
      if (params.turnId !== turnId) return null;
      if (event.method === "item/agentMessage/delta") {
        if (typeof params.delta !== "string") throw new RuntimeFault("CODEX_MESSAGE_INVALID", "OUTPUT_INVALID");
        streamedBytes += Buffer.byteLength(params.delta, "utf8");
        if (streamedBytes > this.settings.maxBytes) throw new RuntimeFault("CODEX_OUTPUT_TOO_LARGE", "OUTPUT_INVALID");
      }
      if (event.method === "item/completed") {
        const item = params.item;
        if (item?.type === "agentMessage") message(item);
        if (item?.status === "failed" || (item?.type === "commandExecution" && item.exitCode !== 0 && item.exitCode !== null)) this.toolFailed = true;
        await attempt.recorder.record("sdk-item-terminal", { callId: item?.id, tool: item?.type, status: item?.status || "completed" });
      }
      if (event.method === "thread/tokenUsage/updated") {
        const usage = params.tokenUsage?.last;
        const known = Number.isSafeInteger(usage?.inputTokens) && Number.isSafeInteger(usage?.outputTokens);
        await attempt.recorder.record("model-usage", { usageKnown: known, usageSource: "app-server-notification",
          ...(known ? { inputTokens: usage.inputTokens, outputTokens: usage.outputTokens, cachedTokens: usage.cachedInputTokens ?? 0 } : {}) });
      }
      return null;
    };
    try {
      await emit({ type: "session-started", sessionId: threadId });
      await attempt.recorder.record("thread-ready", { mode: "ephemeral", readOnly: profile.readOnly, status: "confirmed" });
      this.confirmed = false;
      const turn = await this.transport.request("turn/start", { threadId, input: [{ type: "text", text: execution.prompt }],
        effort: selection?.effort || this.settings.effort, serviceTierForTurn: selection?.fast ? "fast" : "default",
        approvalPolicy: "never", cwd: workspace.cwd,
        sandboxPolicy: sandbox === "read-only" ? { type: "readOnly", networkAccess: this.settings.network }
          : { type: "workspaceWrite", writableRoots: [workspace.cwd], networkAccess: this.settings.network, excludeSlashTmp: true, excludeTmpdirEnvVar: true } }, signal);
      turnId = turn?.turn?.id;
      if (!turnId) throw new RuntimeFault("CODEX_TURN_ID_MISSING");
      for (;;) {
        const terminal = await consume(await events.next(signal));
        if (!terminal) continue;
        if (terminal !== "completed") throw new RuntimeFault("CODEX_TURN_NOT_COMPLETED", "MODEL_ERROR");
        signal.throwIfAborted();
        const output = finalOutput();
        if (!output.trim() || Buffer.byteLength(output, "utf8") > this.settings.maxBytes) throw new RuntimeFault("CODEX_OUTPUT_INVALID", "OUTPUT_INVALID");
        this.assertSuccessful(); return completed(output);
      }
    } catch (error) {
      if (!this.confirmed && turnId) {
        const cleanup = AbortSignal.timeout(this.cfg.cleanupTimeoutMs);
        try {
          await this.transport.request("turn/interrupt", { threadId, turnId }, cleanup);
          while (!this.confirmed) await consume(await events.next(cleanup));
          await attempt.recorder.record("turn-interrupted", { status: "confirmed" });
        } catch { /* The interrupt RPC alone does not confirm turn termination. */ }
      }
      if (!this.confirmed) throw new RuntimeFault("PROCESS_TREE_UNCERTAIN");
      throw error;
    } finally {
      try {
        if (this.confirmed) {
          const released = await this.transport.request("thread/unsubscribe", { threadId }, AbortSignal.timeout(this.cfg.cleanupTimeoutMs));
          if (!["unsubscribed", "notLoaded", "notSubscribed"].includes(released?.status)) throw new RuntimeFault("CODEX_THREAD_RELEASE_UNKNOWN");
          await attempt.recorder.record("thread-released", { status: "confirmed" });
        }
      } catch {
        this.confirmed = false;
        throw new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN");
      } finally { this.transport.unsubscribe(threadId); }
    }
  }
  async settled() { return this.confirmed; }
  assertSuccessful() { if (this.toolFailed) throw new RuntimeFault("UNRESOLVED_TOOL_FAILURE", "OUTPUT_INVALID"); }
}

export async function createCodexAppServerBackend(cfg: WorkerConfig): Promise<WorkerBackend> {
  const settings = await codexSettings(getProfile(cfg.role).readOnly);
  const transport = await AppServerTransport.start(settings);
  return codexBackend(cfg, "codex-app-server", () => new AppServerRuntime(transport, settings, cfg), async () => {
    if (!await transport.close()) throw new RuntimeFault("PROCESS_TREE_UNCERTAIN");
  });
}
