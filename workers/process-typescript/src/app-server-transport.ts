import { CodexProcess, CODEX_EXECUTION_ARGS, type CodexSettings } from "./codex-process.js";
import { RuntimeFault } from "./runtime-core.js";

export class ThreadEvents {
  private events: any[] = [];
  private waiters: Array<{ resolve(value: any): void; reject(error: Error): void }> = [];
  private error?: Error;
  push(event: any) {
    if (this.error) return;
    const waiter = this.waiters.shift();
    if (waiter) waiter.resolve(event);
    else if (this.events.length >= 512) this.fail(new RuntimeFault("CODEX_EVENT_QUEUE_FULL"));
    else this.events.push(event);
  }
  fail(error: Error) { this.error = error; for (const waiter of this.waiters.splice(0)) waiter.reject(error); }
  async next(signal: AbortSignal): Promise<any> {
    signal.throwIfAborted();
    if (this.error) throw this.error;
    if (this.events.length) return this.events.shift();
    return new Promise((resolve, reject) => {
      const abort = () => { const index = this.waiters.indexOf(waiter); if (index >= 0) this.waiters.splice(index, 1); reject(new RuntimeFault("ATTEMPT_CANCELLED")); };
      const waiter = { resolve(value: any) { signal.removeEventListener("abort", abort); resolve(value); },
        reject(error: Error) { signal.removeEventListener("abort", abort); reject(error); } };
      this.waiters.push(waiter); signal.addEventListener("abort", abort, { once: true });
      if (signal.aborted) abort();
    });
  }
}

/** One Worker owns the service; concurrent attempts own separate inboxes. */
export class AppServerTransport {
  private readonly process: CodexProcess;
  private readonly pending = new Map<number, { resolve(value: any): void; reject(error: Error): void }>();
  private readonly threads = new Map<string, ThreadEvents>();
  private sequence = 0;
  private failed?: Error;
  private closing?: Promise<boolean>;
  private constructor(settings: CodexSettings) {
    this.process = new CodexProcess(settings, ["app-server", "--listen", "stdio://", ...CODEX_EXECUTION_ARGS, "--config", `sandbox_workspace_write.network_access=${settings.network}`], process.cwd(), async message => {
      if (message.id !== undefined && !message.method) {
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        if (message.error) pending.reject(new RuntimeFault("CODEX_RPC_REJECTED", "MODEL_ERROR"));
        else pending.resolve(message.result);
      } else if (message.id !== undefined && message.method) {
        if (["item/commandExecution/requestApproval", "item/fileChange/requestApproval"].includes(message.method)) {
          await this.process.send({ id: message.id, result: { decision: "decline" } });
        } else await this.process.send({ id: message.id, error: { code: -32601, message: "Host interaction is not authorized" } });
      } else if (typeof message.method === "string") {
        const threadId = message.params?.threadId ?? message.params?.thread?.id;
        if (typeof threadId === "string") this.threads.get(threadId)?.push(message);
      } else throw new RuntimeFault("CODEX_RPC_MESSAGE_INVALID");
    });
    void this.process.closed.then(() => this.fail(new RuntimeFault("CODEX_APP_SERVER_DISCONNECTED")));
  }
  private fail(error: Error) {
    this.failed ??= error;
    for (const pending of this.pending.values()) pending.reject(error);
    this.pending.clear(); for (const inbox of this.threads.values()) inbox.fail(error);
  }
  static async start(settings: CodexSettings): Promise<AppServerTransport> {
    const transport = new AppServerTransport(settings);
    const signal = AbortSignal.timeout(10000);
    try {
      const initialized = await transport.request("initialize", { clientInfo: { name: "aiw_process_typescript", title: "AIW TypeScript Worker", version: "1.0.0" }, capabilities: { experimentalApi: false } }, signal);
      if (!initialized || typeof initialized.userAgent !== "string") throw new RuntimeFault("CODEX_HANDSHAKE_INVALID", "CONFIGURATION");
      await transport.process.send({ method: "initialized", params: {} });
      await transport.account(signal);
      return transport;
    } catch (error) { await transport.close(); throw error; }
  }
  async account(signal: AbortSignal) {
    const response = await this.request("account/read", { refreshToken: false }, signal);
    if (!response || !response.account || !["apiKey", "chatgpt"].includes(response.account.type)) throw new RuntimeFault("CODEX_ACCOUNT_UNAVAILABLE", "CONFIGURATION");
  }
  async request(method: string, params: unknown, signal: AbortSignal): Promise<any> {
    // Bound individual RPC responses, independently of the potentially long-running model turn.
    signal = AbortSignal.any([signal, AbortSignal.timeout(30000)]);
    signal.throwIfAborted(); if (this.failed) throw this.failed;
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const abort = () => { this.pending.delete(id); signal.removeEventListener("abort", abort); reject(new RuntimeFault("CODEX_RPC_CANCELLED")); };
      this.pending.set(id, { resolve(value) { signal.removeEventListener("abort", abort); resolve(value); },
        reject(error) { signal.removeEventListener("abort", abort); reject(error); } });
      signal.addEventListener("abort", abort, { once: true });
      void this.process.send({ id, method, params }).catch(() => {
        const waiter = this.pending.get(id); this.pending.delete(id); waiter?.reject(new RuntimeFault("CODEX_RPC_SEND_FAILED"));
      });
      if (signal.aborted) abort();
    });
  }
  subscribe(threadId: string): ThreadEvents {
    if (this.threads.has(threadId)) throw new RuntimeFault("CODEX_THREAD_REUSED");
    const inbox = new ThreadEvents(); this.threads.set(threadId, inbox); return inbox;
  }
  unsubscribe(threadId: string) { this.threads.delete(threadId); }
  close(): Promise<boolean> {
    return this.closing ??= this.process.close().finally(() => this.fail(new RuntimeFault("CODEX_APP_SERVER_CLOSED")));
  }
}
