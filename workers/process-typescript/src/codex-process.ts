import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { realpathSync } from "node:fs";
import path from "node:path";
import { RuntimeFault, positiveSetting } from "./runtime-core.js";
import { resolveInstalledCodex } from "./codex-runtime.js";

export const CODEX_VERSION = "0.162.1";
export interface CodexSettings {
  executablePath: string; env: Record<string, string>; model: string; effort: string;
  network: boolean; sandbox: "read-only" | "workspace-write"; maxBytes: number;
}

/** One native process; stdout parsing and callbacks are serialized and bounded. */
export class CodexProcess {
  readonly child: ChildProcessWithoutNullStreams;
  readonly closed: Promise<number>;
  private queue = Promise.resolve();
  private error?: Error;
  private ending = false;
  private exited = false;
  private windowsTreeTerminated = false;
  private buffer = Buffer.alloc(0);
  private queued = 0;
  constructor(settings: CodexSettings, args: string[], cwd: string, onMessage: (value: any) => void | Promise<void>, json = true) {
    this.child = spawn(settings.executablePath, args, { cwd, env: settings.env, shell: false,
      windowsHide: true, detached: process.platform !== "win32", stdio: ["pipe", "pipe", "pipe"] });
    this.closed = new Promise<number>(resolve => {
      this.child.once("error", () => { this.error = new RuntimeFault("CODEX_PROCESS_START_FAILED", "CONFIGURATION"); resolve(-1); });
      this.child.once("close", code => { this.exited = true; resolve(code ?? -1); });
    });
    this.child.stdin.on("error", () => this.fail("CODEX_STDIN_FAILED"));
    this.child.stderr.on("data", () => { /* Drain without retaining credentials or task text. */ });
    this.child.stdout.on("data", chunk => {
      if (this.error) return;
      this.buffer = Buffer.concat([this.buffer, chunk]);
      if (this.buffer.length > settings.maxBytes) { this.fail("CODEX_FRAME_TOO_LARGE"); return; }
      for (;;) {
        const end = this.buffer.indexOf(10); if (end < 0) break;
        const line = this.buffer.subarray(0, end).toString("utf8"); this.buffer = this.buffer.subarray(end + 1);
        if (!line.trim()) continue;
        if (++this.queued > 512) { this.fail("CODEX_EVENT_QUEUE_FULL"); return; }
        this.queue = this.queue.then(async () => {
          this.queued--;
          if (this.error) return;
          await onMessage(json ? JSON.parse(line) : line);
        }).catch(error => this.fail(error instanceof RuntimeFault ? error : "CODEX_EVENT_INVALID"));
      }
    });
  }
  private fail(code: string | Error) {
    if (this.error) return;
    this.error = typeof code === "string" ? new RuntimeFault(code, "MODEL_ERROR") : code;
    this.child.stdin.end();
    void this.terminate();
  }
  async send(value: unknown) {
    if (this.error || this.ending || this.exited) throw this.error ?? new RuntimeFault("CODEX_CONNECTION_CLOSED");
    await new Promise<void>((resolve, reject) => this.child.stdin.write(JSON.stringify(value) + "\n", error => error ? reject(new RuntimeFault("CODEX_STDIN_FAILED")) : resolve()));
  }
  async input(text: string) {
    await new Promise<void>((resolve, reject) => this.child.stdin.end(text, (error?: Error | null) => error ? reject(new RuntimeFault("CODEX_STDIN_FAILED")) : resolve()));
  }
  async finish(timeoutMs = 10000): Promise<number> {
    const code = await this.bounded(this.closed, timeoutMs);
    await this.bounded(this.queue, timeoutMs);
    if (this.error) throw this.error;
    if (this.buffer.toString("utf8").trim()) throw new RuntimeFault("CODEX_FRAME_INCOMPLETE");
    return code;
  }
  private async bounded<T>(operation: Promise<T>, timeoutMs: number): Promise<T> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try { return await Promise.race([operation, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new RuntimeFault("PROCESS_TREE_UNCERTAIN")), timeoutMs);
    })]); } finally { clearTimeout(timer); }
  }
  async close(timeoutMs = 10000): Promise<boolean> {
    this.ending = true; this.child.stdin.end();
    try { await this.bounded(this.closed, Math.min(timeoutMs, 2000)); }
    catch { return this.terminate(timeoutMs); }
    return this.treeGone() || await this.terminate(timeoutMs);
  }
  private treeGone(): boolean {
    if (!this.exited) return false;
    if (process.platform === "win32") return this.windowsTreeTerminated;
    if (!this.child.pid) return true;
    try { process.kill(-this.child.pid, 0); return false; }
    catch (error) { return (error as NodeJS.ErrnoException).code === "ESRCH"; }
  }
  async terminate(timeoutMs = 10000): Promise<boolean> {
    this.ending = true;
    const pid = this.child.pid;
    if (!pid) return this.exited;
    if (process.platform === "win32") {
      const killer = spawn(path.join(process.env.SystemRoot || "C:\\Windows", "System32", "taskkill.exe"), ["/PID", String(pid), "/T", "/F"], { stdio: "ignore", windowsHide: true });
      try {
        const code = await this.bounded(new Promise<number>(resolve => { killer.once("error", () => resolve(-1)); killer.once("close", code => resolve(code ?? -1)); }), timeoutMs);
        if (code !== 0) return false;
        this.windowsTreeTerminated = true;
      } catch { killer.kill(); killer.unref(); return false; }
    } else {
      try { process.kill(-pid, "SIGKILL"); }
      catch (error) { if ((error as NodeJS.ErrnoException).code !== "ESRCH") return false; }
    }
    try { await this.bounded(this.closed, timeoutMs); return this.treeGone(); }
    catch { return false; }
  }
}

export async function codexSettings(roleReadOnly: boolean): Promise<CodexSettings> {
  // Node's native API cannot prove descendant termination after a Windows
  // parent exits. Keep that platform unsupported before sending task text.
  if (process.platform === "win32") throw new RuntimeFault("CODEX_WINDOWS_PROCESS_TREE_UNVERIFIED", "CONFIGURATION");
  const installed = resolveInstalledCodex(process.env.AIW_CODEX_PATH);
  const allowed = /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|TEMP|TMP|HOME|USERPROFILE|LANG|LC_ALL|CODEX_HOME|OPENAI_API_KEY|CODEX_API_KEY|SSH_AUTH_SOCK)$/i;
  const env = Object.fromEntries(Object.entries(installed.env).filter(([key]) => allowed.test(key)));
  const configured = process.env.AIW_CODEX_SANDBOX || (roleReadOnly ? "read-only" : "workspace-write");
  if (!["read-only", "workspace-write"].includes(configured) || (roleReadOnly && configured !== "read-only")) throw new RuntimeFault("CODEX_PERMISSION_INVALID", "CONFIGURATION");
  if ((process.env.AIW_CODEX_APPROVAL_POLICY || "never") !== "never") throw new RuntimeFault("CODEX_INTERACTIVE_APPROVAL_UNSUPPORTED", "CONFIGURATION");
  if (/^(1|true|yes)$/i.test(process.env.AIW_CODEX_NETWORK_ACCESS || "false")) throw new RuntimeFault("WORKER_NETWORK_ACCESS_FORBIDDEN", "CONFIGURATION");
  const effort = process.env.AIW_CODEX_REASONING_EFFORT || "medium";
  if (!["none", "minimal", "low", "medium", "high", "xhigh"].includes(effort)) throw new RuntimeFault("CODEX_EFFORT_INVALID", "CONFIGURATION");
  const settings: CodexSettings = { ...installed, env, sandbox: configured as CodexSettings["sandbox"],
    network: false,
    model: process.env.AIW_CODEX_MODEL || "", effort, maxBytes: positiveSetting("AIW_CODEX_MAX_OUTPUT_BYTES", 4 * 1024 * 1024) };
  await verifyCodexVersion(settings);
  return settings;
}

export function assertCredentialBoundary(settings: CodexSettings, workspace: string) {
  const home = settings.env.CODEX_HOME;
  if (!home) return;
  const relative = path.relative(realpathSync(workspace), realpathSync(home));
  if (!relative || (!path.isAbsolute(relative) && relative !== ".." && !relative.startsWith(`..${path.sep}`))) throw new RuntimeFault("CODEX_HOME_INSIDE_WORKSPACE", "CONFIGURATION");
}

export async function verifyCodexVersion(settings: CodexSettings): Promise<void> {
  let version = "";
  const check = new CodexProcess(settings, ["--version"], process.cwd(), line => { version += line + "\n"; if (version.length > 4096) throw new RuntimeFault("CODEX_VERSION_INVALID"); }, false);
  try { if (await check.finish(5000) !== 0 || !version.trim().split(/\s+/).includes(CODEX_VERSION)) throw new RuntimeFault("CODEX_VERSION_MISMATCH", "CONFIGURATION"); }
  catch (error) { await check.terminate(5000); throw error; }
}
