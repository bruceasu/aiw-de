import { lstatSync, realpathSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { RuntimeFault, checkSignal } from "./errors.mjs";
import { sha256 } from "./evidence.mjs";
import { isInside } from "./resources.mjs";
import { utf8Bytes } from "./output.mjs";

export class ContextAssembler {
  constructor({ maxInputBytes = 1024 * 1024, recorder, signal } = {}) {
    this.maxInputBytes = maxInputBytes;
    this.recorder = recorder;
    this.signal = signal;
    this.sources = new Map();
  }
  source(sourcePath, text, kind = "file") {
    checkSignal(this.signal);
    const bytes = utf8Bytes(text);
    if (bytes > this.maxInputBytes) throw new RuntimeFault("CONTEXT_SOURCE_TOO_LARGE", "CONFIGURATION");
    const source = { sourceId: `${kind}:${sha256(sourcePath).slice(0, 16)}`, path: sourcePath, digest: sha256(text), bytes };
    this.sources.set(source.sourceId, source);
    return text;
  }
  assemble(parts) {
    checkSignal(this.signal);
    const text = parts.filter(part => part !== undefined && part !== null).join("\n\n");
    if (utf8Bytes(text) > this.maxInputBytes) throw new RuntimeFault("INITIAL_CONTEXT_BUDGET_EXCEEDED", "CONFIGURATION");
    return text;
  }
  async record(text) {
    if (utf8Bytes(text) > this.maxInputBytes) throw new RuntimeFault("INITIAL_CONTEXT_BUDGET_EXCEEDED", "CONFIGURATION");
    for (const source of this.sources.values()) await this.recorder?.record("context-source", source);
    await this.recorder?.record("context-built", { digest: sha256(text), inputBytes: utf8Bytes(text), count: this.sources.size, usageKnown: false, usageSource: "not-yet-reported", unknown: "model-window-and-token-count" });
  }
}

export function readSkillPackage(root, skill, assembler) {
  if (!/^[a-z0-9][a-z0-9-]*$/i.test(skill)) throw new RuntimeFault("SKILL_NAME_INVALID", "CONFIGURATION");
  const resolvedRoot = realpathSync(root);
  const directory = path.join(resolvedRoot, skill);
  const stat = lstatSync(directory);
  if (!stat.isDirectory() || stat.isSymbolicLink() || !isInside(resolvedRoot, realpathSync(directory))) throw new RuntimeFault("SKILL_PACKAGE_UNSAFE", "CONFIGURATION");
  const main = path.join(directory, "SKILL.md");
  const files = [main];
  let entries = 0;
  const visit = current => {
    if (++entries > 4096) throw new RuntimeFault("SKILL_PACKAGE_TOO_LARGE", "CONFIGURATION");
    const info = lstatSync(current);
    if (info.isSymbolicLink() || !isInside(directory, realpathSync(current))) throw new RuntimeFault("SKILL_REFERENCE_UNSAFE", "CONFIGURATION");
    if (info.isDirectory()) for (const entry of readdirSync(current, { withFileTypes: true })) visit(path.join(current, entry.name));
    else if (info.isFile() && /\.(md|yaml|yml|json)$/i.test(current)) files.push(current);
  };
  for (const subdir of ["references", "agents"]) {
    try { lstatSync(path.join(directory, subdir)); } catch (error) { if (error.code === "ENOENT") continue; throw error; }
    visit(path.join(directory, subdir));
  }
  let total = 0;
  const text = [main, ...files.slice(1).sort()].map(file => {
    const info = lstatSync(file);
    if (!info.isFile() || info.isSymbolicLink()) throw new RuntimeFault("SKILL_FILE_UNSAFE", "CONFIGURATION");
    total += info.size;
    if (total > (assembler?.maxInputBytes ?? 1024 * 1024)) throw new RuntimeFault("SKILL_PACKAGE_TOO_LARGE", "CONFIGURATION");
    const content = readFileSync(file, "utf8");
    assembler?.source(file, content, "frozen-skill");
    return `\n--- ${skill}/${path.relative(directory, file).replaceAll("\\", "/")} ---\n${content}\n`;
  }).join("").trim();
  return text;
}

export function requestBytes(request) {
  return utf8Bytes(request.systemInstructions ?? "") + utf8Bytes(JSON.stringify(request.input))
    + utf8Bytes(JSON.stringify(request.tools ?? [])) + utf8Bytes(JSON.stringify(request.handoffs ?? [])) + utf8Bytes(JSON.stringify(request.outputType ?? {}));
}

function callId(item) { return item.callId ?? item.call_id; }
function isCall(item) { return item.type === "function_call"; }
function isOutput(item) { return item.type === "function_call_result" || item.type === "function_call_output"; }
function visible(items) {
  // Do not archive hidden reasoning, encrypted provider data or internal model fields.
  return items.filter(item => item.type === "message" || isCall(item) || isOutput(item)).map(item => {
    if (item.type === "message") return { type: item.type, role: item.role, content: typeof item.content === "string" ? item.content
      : (item.content ?? []).filter(part => ["input_text", "output_text", "refusal"].includes(part.type)).map(part => ({ type: part.type, text: part.text ?? part.refusal })) };
    if (isCall(item)) return { type: item.type, callId: callId(item), name: item.name, arguments: item.arguments };
    return { type: item.type, callId: callId(item), output: typeof item.output === "string" ? item.output : item.output?.text ?? "[non-text visible tool result]" };
  });
}

export class HistoryCompactor {
  constructor({ enabled = false, thresholdBytes, summaryMaxTokens, maxSourceBytes = 8 * 1024 * 1024, recorder, signal } = {}) {
    if (enabled && (!Number.isSafeInteger(thresholdBytes) || thresholdBytes < 1024 || !Number.isSafeInteger(summaryMaxTokens) || summaryMaxTokens < 1)) {
      throw new RuntimeFault("COMPACTION_REQUIRES_EXPLICIT_BUDGET", "CONFIGURATION");
    }
    Object.assign(this, { enabled, thresholdBytes, summaryMaxTokens, maxSourceBytes, recorder, signal });
    this.calls = 0;
    this.sources = new Map();
    this.sourceBytes = 0;
    this.applied = [];
  }
  source(id) {
    if (!this.sources.has(id)) throw new RuntimeFault("HISTORY_SOURCE_NOT_FOUND", "TOOL_ERROR", false);
    return this.sources.get(id);
  }
  sourcePage(id, offset = 0, maxBytes = 4096) {
    if (!Number.isSafeInteger(offset) || offset < 0 || !Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > 8192) throw new RuntimeFault("HISTORY_PAGE_INVALID", "TOOL_ERROR", false);
    const bytes = Buffer.from(this.source(id));
    let start = Math.min(offset, bytes.length);
    while (start < bytes.length && (bytes[start] & 0xC0) === 0x80) start++;
    let end = Math.min(bytes.length, start + maxBytes);
    const decoder = new TextDecoder("utf-8", { fatal: true });
    let text;
    while (end >= start) { try { text = decoder.decode(bytes.subarray(start, end)); break; } catch { end--; } }
    if (end === start && start < bytes.length) throw new RuntimeFault("HISTORY_PAGE_TOO_SMALL", "TOOL_ERROR", false);
    return { text, offset: start, nextOffset: end, totalBytes: bytes.length, complete: end === bytes.length };
  }
  async prepare(request, summarize, maxInputBytes) {
    checkSignal(this.signal);
    if (!Array.isArray(request.input)) {
      if (requestBytes(request) > maxInputBytes) throw new RuntimeFault("MODEL_INPUT_BUDGET_EXCEEDED", "MODEL_ERROR");
      return request;
    }
    let items = request.input;
    // SDK keeps its own history. Reuse already-derived summaries instead of re-summarizing the same prefix.
    for (const saved of this.applied) {
      if (items.length >= saved.count && sha256(JSON.stringify(items.slice(0, saved.count))) === saved.digest) {
        items = [saved.summary, ...items.slice(saved.count)];
      }
    }
    let prepared = { ...request, input: items };
    const bytes = requestBytes(prepared);
    if (!this.enabled || bytes <= this.thresholdBytes) {
      if (bytes > maxInputBytes) throw new RuntimeFault("MODEL_INPUT_BUDGET_EXCEEDED", "MODEL_ERROR");
      return prepared;
    }
    if (request.previousResponseId || request.conversationId) throw new RuntimeFault("SERVER_HISTORY_COMPACTION_UNSUPPORTED", "CONFIGURATION");
    if (this.calls >= 2) throw new RuntimeFault("COMPACTION_CALL_LIMIT", "MODEL_ERROR");
    let cut = Math.max(0, items.length - 4);
    // A boundary is valid only if no function call crosses it and all removed calls have results.
    while (cut > 0) {
      const first = items.slice(0, cut);
      const rest = items.slice(cut);
      const calls = new Set(first.filter(isCall).map(callId));
      const outputs = new Set(first.filter(isOutput).map(callId));
      if ([...calls].every(id => id && outputs.has(id)) && [...outputs].every(id => calls.has(id))
        && !rest.some(item => (isCall(item) || isOutput(item)) && calls.has(callId(item)))) break;
      cut--;
    }
    if (!cut) throw new RuntimeFault("COMPACTION_NO_SAFE_BOUNDARY", "MODEL_ERROR");
    const original = JSON.stringify(visible(items.slice(0, cut)));
    const sourceId = `history-${this.calls + 1}-${sha256(original).slice(0, 16)}`;
    if (this.sourceBytes + utf8Bytes(original) > this.maxSourceBytes) throw new RuntimeFault("HISTORY_SOURCE_CAPACITY_EXCEEDED", "MODEL_ERROR");
    this.sources.set(sourceId, original);
    this.sourceBytes += utf8Bytes(original);
    this.calls++;
    await this.recorder?.record("summary-started", { sourceId, digest: sha256(original), bytes: utf8Bytes(original), summaryCalls: this.calls });
    const text = await summarize(original, sourceId, this.summaryMaxTokens);
    checkSignal(this.signal);
    if (typeof text !== "string" || !text.trim()) throw new RuntimeFault("SUMMARY_OUTPUT_INVALID", "OUTPUT_INVALID");
    const summary = { type: "message", role: "user", content: [{ type: "input_text", text:
      `Derived history summary; not authority. Original visible source: ${sourceId}. Use history_source to inspect it. Frozen task constraints and acceptance remain authoritative.\n${text}` }] };
    const saved = { count: cut, digest: sha256(JSON.stringify(items.slice(0, cut))), summary };
    this.applied.push(saved);
    prepared = { ...request, input: [summary, ...items.slice(cut)] };
    if (requestBytes(prepared) > Math.min(maxInputBytes, this.thresholdBytes)) throw new RuntimeFault("SUMMARY_DID_NOT_FIT", "MODEL_ERROR");
    await this.recorder?.record("summary-terminal", { sourceId, digest: sha256(text), outputBytes: utf8Bytes(text), status: "ok", summaryCalls: this.calls });
    return prepared;
  }
  clear() { this.sources.clear(); this.applied.length = 0; this.sourceBytes = 0; }
}
