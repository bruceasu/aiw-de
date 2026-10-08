import { randomUUID } from "node:crypto";
import { RuntimeFault, checkSignal, errorCode } from "./errors.mjs";
import { safePath, ResourceCoordinator } from "./resources.mjs";
import { toolOutput, utf8Bytes } from "./output.mjs";
import { sha256 } from "./evidence.mjs";

export class ToolHost {
  #active = new Set();
  #failures = new Map();
  #calls = new Map();
  #known = new Set();
  constructor({ cwd, readOnly, allowShell, signal, recorder, coordinator, abort, maxOutputBytes = 24000, toolTimeoutMs = 60000 }) {
    Object.assign(this, { cwd, readOnly, allowShell, signal, recorder, coordinator, abort, maxOutputBytes, toolTimeoutMs });
  }
  async execute(definition, args, { callId = randomUUID(), parentCallId } = {}) {
    const parent = parentCallId ? this.#calls.get(parentCallId) : undefined;
    if ((parentCallId && !parent) || (parent?.depth ?? -1) >= 4 || this.#known.has(callId)) throw new RuntimeFault("NESTED_TOOL_CORRELATION_INVALID");
    if (this.#known.size >= 4096) throw new RuntimeFault("TOOL_CALL_LIMIT");
    this.#known.add(callId);
    this.#calls.set(callId, { depth: (parent?.depth ?? -1) + 1 });
    const operation = this.#execute(definition, args, callId, parentCallId);
    this.#active.add(operation);
    try { return await operation; }
    finally { this.#active.delete(operation); this.#calls.delete(callId); }
  }
  async #execute(definition, args, callId, parentCallId) {
    const started = Date.now();
    let operationStarted = false;
    const failureKey = `${definition.name}:${args?.path ?? sha256(JSON.stringify(args ?? {}))}`;
    const timerController = new AbortController();
    const timer = setTimeout(() => { timerController.abort(new RuntimeFault("TOOL_DEADLINE_EXCEEDED", "TIMEOUT")); this.abort?.("tool-timeout"); }, this.toolTimeoutMs);
    const signal = AbortSignal.any([this.signal, timerController.signal]);
    try {
      checkSignal(signal);
      if (definition.write && this.readOnly) throw new RuntimeFault("READ_ONLY_OPERATION_DENIED", "CONFIGURATION");
      if (definition.shell && !this.allowShell) throw new RuntimeFault("SHELL_OPERATION_DENIED", "CONFIGURATION");
      if (definition.validate && !definition.validate(args)) throw new RuntimeFault("TOOL_ARGUMENT_INVALID", "TOOL_ERROR", false);
      const file = definition.file ? await safePath(this.cwd, args.path, definition.write) : undefined;
      const request = definition.resource ?? (file ? { mode: definition.write ? "IX" : "IS", files: [{ key: ResourceCoordinator.fileKey(file), mode: definition.write ? "X" : "S" }] } : { mode: "S" });
      await this.recorder.record("tool-started", { callId, parentCallId, tool: definition.name, operation: definition.write ? "write" : "read", path: file,
        digest: sha256(JSON.stringify(args ?? {})), sideEffect: definition.write ? "possible" : "none" });
      const run = async () => {
        checkSignal(signal);
        if (definition.file) await safePath(this.cwd, args.path, definition.write);
        operationStarted = true;
        const value = await definition.run(args, { file, signal, callId, host: this });
        const result = typeof value === "string" ? toolOutput(value, { maxBytes: this.maxOutputBytes })
          : toolOutput(value.content ?? "", { ...value, maxBytes: this.maxOutputBytes });
        checkSignal(signal);
        const failed = typeof value !== "string" && value.status === "error";
        await this.recorder.record("tool-terminal", { callId, parentCallId, tool: definition.name, status: failed ? "error" : "ok", complete: JSON.parse(result).complete,
          truncated: JSON.parse(result).truncated, outputBytes: utf8Bytes(result), durationMs: Date.now() - started,
          digest: typeof value === "string" ? undefined : value.data?.resultDigest ?? value.data?.sourceDigest,
          sideEffect: definition.write ? "completed" : "none" });
        if (failed) this.#failures.set(failureKey, "TOOL_RESULT_FAILED");
        else this.#failures.delete(failureKey);
        return result;
      };
      // Composition holds no resource slot: each child acquires its own lease through execute.
      // This avoids holding a parent slot while all slots are waiting for nested children.
      if (definition.compose) return await run();
      return await this.coordinator.run(request, signal, run);
    } catch (originalError) {
      const error = ["ENOENT", "ENOTDIR", "EISDIR"].includes(originalError.code)
        ? new RuntimeFault("WORKSPACE_FILE_NOT_FOUND", "TOOL_ERROR", false) : originalError;
      this.#failures.set(failureKey, errorCode(error));
      try { await this.recorder.record("tool-terminal", { callId, parentCallId, tool: definition.name, status: "error", code: errorCode(error), durationMs: Date.now() - started,
        sideEffect: definition.write && operationStarted ? "unknown" : "none" }); }
      catch (evidenceError) { this.abort?.("critical-evidence"); throw evidenceError; }
      if (signal.aborted || !(error instanceof RuntimeFault) || error.fatal) {
        this.abort?.(errorCode(error));
        throw error;
      }
      return toolOutput(error.code, { status: "error", complete: false, maxBytes: this.maxOutputBytes });
    } finally { clearTimeout(timer); }
  }
  async settled(timeoutMs = 10000) {
    if (!this.#active.size) return true;
    let timer;
    try { return await Promise.race([Promise.allSettled([...this.#active]).then(() => true), new Promise(resolve => { timer = setTimeout(() => resolve(false), timeoutMs); })]); }
    finally { clearTimeout(timer); }
  }
  assertSuccessful() {
    this.recorder.assertHealthy();
    if (this.#failures.size) throw new RuntimeFault("UNRESOLVED_TOOL_FAILURE", "OUTPUT_INVALID");
  }
}
