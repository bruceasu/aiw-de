import type { AttemptExecution } from "../runtime-core/index.mjs";

/** Backend-neutral input owned by the Worker attempt, never by an SDK. */
export interface AgentRunRequest {
  execution: AttemptExecution;
  emit(event: AgentEvent): void | Promise<void>;
}

export type AgentEvent =
  | { type: "session-started"; sessionId: string }
  | { type: "runtime-event"; name: string };

export interface AgentRunResult {
  status: "completed";
  output: string;
  /** No backend-independent continuation state is supported in this FD. */
  state: null;
}

export interface AgentRuntimeCapabilities {
  resume: false;
  cancelById: false;
  /** Cancellation is scoped to the current attempt's AbortSignal. */
  attemptCancellation: true;
  eventStreaming: boolean;
  tools: boolean;
}

/** Local execution engine. The Control Plane still owns task and lease state. */
export interface AgentRuntime {
  readonly type: "codex-cli" | "codex-app-server" | "openai-agents";
  readonly capabilities: AgentRuntimeCapabilities;
  run(request: AgentRunRequest): Promise<AgentRunResult>;
  settled?(timeoutMs?: number): Promise<void | boolean>;
  assertSuccessful?(): void;
  clear?(): void;
}

export function completed(output: string): AgentRunResult {
  return { status: "completed", output, state: null };
}
