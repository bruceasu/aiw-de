import type { WorkerBackend, WorkerConfig } from "./index.js";
import type { AgentRuntime } from "./agent-runtime.js";
import { buildPrompt, completionReport, resolveTaskProfile } from "./instructions.js";
import { chooseAiProfile } from "./ai-profiles.js";
import { CODEX_VERSION, CODEX_SHELL_INSTRUCTIONS } from "./codex-process.js";
import { sha256 } from "./runtime-core.js";

export function codexBackend(cfg: WorkerConfig, mode: "codex-cli" | "codex-app-server", engine: () => AgentRuntime, shutdown?: () => Promise<void>): WorkerBackend {
  return {
    startup: `using system Codex ${CODEX_VERSION} mode=${mode}; local delivery; production admission belongs to FD-015`, shutdown,
    createAdapter: () => {
      const runtime = engine();
      let profile: ReturnType<typeof resolveTaskProfile>;
      return {
        runtime: mode, versions: { codex: CODEX_VERSION },
        select: task => task.aiGuidance ? chooseAiProfile(task.aiGuidance, mode) : null,
        profile: task => (profile = resolveTaskProfile(task, cfg.role)),
        prompt: (task, context) => `${CODEX_SHELL_INSTRUCTIONS}\n\n${buildPrompt(task, cfg.role, cfg.skillsRoot, context, profile)}`,
        execute: async execution => {
          await execution.context.record(execution.prompt);
          await execution.attempt.recorder.record("runtime-capabilities", { capability: `${mode};resume=false;attemptCancellation=true;eventStreaming=true;tools=true`,
            processTree: mode === "codex-cli" ? "attempt-process-tree" : "shared-server;per-thread-turn-interrupt", unknown: "production-no-store-and-breakaway-processes;FD-015" });
          return await execution.workspace.coordinator.run({ mode: "X" }, execution.attempt.signal, async () => (await runtime.run({ execution, emit: event => {
            if (event.type === "session-started") execution.onSession(event.sessionId);
          } })).output);
        },
        report: completionReport, digest: sha256,
        settled: timeout => runtime.settled?.(timeout) ?? Promise.resolve(true),
        assertSuccessful: () => runtime.assertSuccessful?.(), clear: () => runtime.clear?.(),
      };
    },
  };
}
