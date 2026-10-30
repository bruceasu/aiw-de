import { spawn } from "node:child_process";
import path from "node:path";
import { RuntimeFault, checkSignal } from "./errors.mjs";
import { TOOL_CACHE_ENV_NAMES } from "./cache.mjs";

export function commandEnvironment(extra = {}) {
  const allowed = /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|TEMP|TMP|HOME|USERPROFILE|HOMEDRIVE|HOMEPATH|LANG|LC_ALL|SSH_AUTH_SOCK|GIT_SSH|GIT_SSH_COMMAND)$/i;
  const permitted = key => allowed.test(key) || TOOL_CACHE_ENV_NAMES.includes(key);
  const result = Object.fromEntries(Object.entries(process.env).filter(([key, value]) => value !== undefined && permitted(key)));
  // Callers cannot accidentally forward Control Plane or model credentials.
  for (const [key, value] of Object.entries(extra)) {
    if (!permitted(key)) throw new RuntimeFault("CHILD_ENVIRONMENT_FORBIDDEN", "CONFIGURATION");
    result[key] = value;
  }
  result.GIT_TERMINAL_PROMPT = "0";
  return result;
}

class Capture {
  constructor(limit, exact) { this.limit = limit; this.exact = exact; this.head = Buffer.alloc(0); this.tail = Buffer.alloc(0); this.bytes = 0; }
  add(buffer) {
    this.bytes += buffer.length;
    if (this.exact) {
      if (this.head.length < this.limit) this.head = Buffer.concat([this.head, buffer.subarray(0, this.limit - this.head.length)]);
    } else {
      const half = Math.floor(this.limit / 2);
      if (this.head.length < half) this.head = Buffer.concat([this.head, buffer.subarray(0, half - this.head.length)]);
      this.tail = Buffer.concat([this.tail, buffer]).subarray(-half);
    }
  }
  value() {
    if (this.exact || this.bytes <= this.head.length) return this.head.toString("utf8");
    if (this.bytes <= this.limit) {
      const overlap = Math.max(0, this.head.length + this.tail.length - this.bytes);
      return Buffer.concat([this.head, this.tail.subarray(overlap)]).toString("utf8");
    }
    // TextDecoder avoids turning a split UTF-8 boundary into replacement characters.
    const decodeEdge = (buffer, tail) => {
      const decoder = new TextDecoder("utf-8", { fatal: true });
      for (let n = 0; n < 4; n++) {
        try { return decoder.decode(tail ? buffer.subarray(n) : buffer.subarray(0, buffer.length - n)); } catch {}
      }
      return buffer.toString("utf8");
    };
    return decodeEdge(this.head, false) + `\n[process output truncated; ${this.bytes} bytes]\n` + decodeEdge(this.tail, true);
  }
}

export async function runCommand(executable, args, { cwd, signal, timeoutMs = 60000, maxBytes = 1024 * 1024, exact = false, env = commandEnvironment(), allowFailure = false } = {}) {
  checkSignal(signal);
  const local = new AbortController();
  const timer = setTimeout(() => local.abort(), timeoutMs);
  const combined = signal ? AbortSignal.any([signal, local.signal]) : local.signal;
  const stdout = new Capture(maxBytes, exact);
  const stderr = new Capture(maxBytes, false);
  let child;
  let cancelled = false;
  let uncertain = false;
  let killPromise = Promise.resolve();
  let forceTimer;
  let rejectExit;
  const terminate = () => {
    if (cancelled || !child?.pid) return;
    cancelled = true;
    forceTimer = setTimeout(() => {
      uncertain = true;
      child.stdout?.destroy();
      child.stderr?.destroy();
      child.unref();
      rejectExit?.(new RuntimeFault("PROCESS_TREE_UNCERTAIN"));
    }, 5000);
    if (process.platform === "win32") {
      const executable = path.join(process.env.SystemRoot || "C:\\Windows", "System32", "taskkill.exe");
      killPromise = new Promise(resolve => {
        const killer = spawn(executable, ["/PID", String(child.pid), "/T", "/F"], { windowsHide: true, stdio: "ignore", env: commandEnvironment() });
        const deadline = setTimeout(() => { uncertain = true; killer.kill(); killer.unref(); resolve(); }, 5000);
        killer.once("error", () => { uncertain = true; clearTimeout(deadline); resolve(); });
        killer.once("close", code => { if (code !== 0) uncertain = true; clearTimeout(deadline); resolve(); });
      });
    } else {
      try { process.kill(-child.pid, "SIGKILL"); } catch (error) { if (error.code !== "ESRCH") uncertain = true; }
    }
  };
  combined.addEventListener("abort", terminate, { once: true });
  try {
    const exitCode = await new Promise((resolve, reject) => {
      rejectExit = reject;
      child = spawn(executable, args, { cwd, env, shell: false, windowsHide: true, detached: process.platform !== "win32", stdio: ["ignore", "pipe", "pipe"] });
      child.stdout.on("data", value => stdout.add(value));
      child.stderr.on("data", value => stderr.add(value));
      child.once("error", () => reject(new RuntimeFault("PROCESS_START_FAILED")));
      child.once("close", code => resolve(code ?? -1));
      if (combined.aborted) terminate();
    });
    await killPromise;
    if (uncertain) throw new RuntimeFault("PROCESS_TREE_UNCERTAIN");
    if (cancelled) throw new RuntimeFault(local.signal.aborted && !signal?.aborted ? "PROCESS_TIMEOUT" : "ATTEMPT_CANCELLED", local.signal.aborted && !signal?.aborted ? "TIMEOUT" : "TOOL_ERROR");
    if (exact && stdout.bytes > maxBytes) throw new RuntimeFault("PROCESS_OUTPUT_TOO_LARGE");
    if (exitCode !== 0 && !allowFailure) throw new RuntimeFault("PROCESS_EXIT_FAILED");
    return { exitCode, stdout: stdout.value(), stderr: stderr.value(), complete: stdout.bytes <= maxBytes && stderr.bytes <= maxBytes,
      processTree: process.platform === "win32" ? "taskkill-on-cancel; breakaway-unknown" : "process-group; breakaway-unknown" };
  } finally {
    clearTimeout(timer);
    clearTimeout(forceTimer);
    combined.removeEventListener("abort", terminate);
  }
}
