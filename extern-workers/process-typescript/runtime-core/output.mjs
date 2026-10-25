import { RuntimeFault } from "./errors.mjs";

const decoder = new TextDecoder("utf-8", { fatal: true });
export const utf8Bytes = value => Buffer.byteLength(String(value), "utf8");

function prefix(text, bytes) {
  const buffer = Buffer.from(text);
  let end = Math.min(buffer.length, Math.max(0, bytes));
  while (end > 0) {
    try { return decoder.decode(buffer.subarray(0, end)); } catch { end--; }
  }
  return "";
}

function suffix(text, bytes) {
  const buffer = Buffer.from(text);
  let start = Math.max(0, buffer.length - Math.max(0, bytes));
  while (start < buffer.length) {
    try { return decoder.decode(buffer.subarray(start)); } catch { start++; }
  }
  return "";
}

export function boundedText(value, { maxBytes = 24000, maxLines = 1000, mode = "head" } = {}) {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 128 || !Number.isSafeInteger(maxLines) || maxLines < 3) {
    throw new RuntimeFault("OUTPUT_BUDGET_INVALID", "CONFIGURATION");
  }
  const original = String(value);
  const originalBytes = utf8Bytes(original);
  const lines = original.split("\n");
  const originalLines = lines.length;
  if (originalBytes <= maxBytes && originalLines <= maxLines) {
    return { text: original, truncated: false, complete: true, originalBytes, originalLines, mode };
  }
  const marker = `\n[truncated; original=${originalBytes} UTF-8 bytes, ${originalLines} lines; mode=${mode}]\n`;
  const allowance = maxBytes - utf8Bytes(marker);
  const lineAllowance = maxLines - 2;
  let text;
  if (mode === "tail") {
    text = marker + suffix(lines.slice(-lineAllowance).join("\n"), allowance);
  } else if (mode === "middle") {
    const first = Math.ceil(lineAllowance / 2);
    text = prefix(lines.slice(0, first).join("\n"), Math.floor(allowance / 2)) + marker
      + suffix(lines.slice(-(lineAllowance - first)).join("\n"), allowance - Math.floor(allowance / 2));
  } else {
    text = prefix(lines.slice(0, lineAllowance).join("\n"), allowance) + marker;
  }
  return { text, truncated: true, complete: false, originalBytes, originalLines, mode };
}

export function toolOutput(content, { maxBytes = 24000, maxLines = 1000, mode = "head", complete = true, status = "ok", data } = {}) {
  // Bound the entire JSON envelope, including its labels and truncation marker.
  const base = { status, complete, truncated: false, content: "", ...(data === undefined ? {} : { data }) };
  const overhead = utf8Bytes(JSON.stringify(base));
  if (overhead + 160 > maxBytes) throw new RuntimeFault("STRUCTURED_OUTPUT_TOO_LARGE", "TOOL_ERROR", false);
  let budget = maxBytes - overhead;
  for (;;) {
    const bounded = boundedText(content, { maxBytes: budget, maxLines, mode });
    const result = JSON.stringify({ ...base, content: bounded.text, complete: complete && bounded.complete, truncated: bounded.truncated });
    const excess = utf8Bytes(result) - maxBytes;
    if (excess <= 0) return result;
    // JSON escaping can enlarge tabs, quotes and line breaks. Never cut serialized JSON.
    budget -= Math.max(excess, 16);
    if (budget < 128) throw new RuntimeFault("STRUCTURED_OUTPUT_TOO_LARGE", "TOOL_ERROR", false);
  }
}

export function checkedJson(text, maxBytes, validate) {
  if (utf8Bytes(text) > maxBytes) throw new RuntimeFault("JSON_OUTPUT_TOO_LARGE", "TOOL_ERROR", false);
  let value;
  try { value = JSON.parse(text); } catch { throw new RuntimeFault("JSON_OUTPUT_INVALID", "TOOL_ERROR", false); }
  if (!validate(value)) throw new RuntimeFault("JSON_SCHEMA_INVALID", "TOOL_ERROR", false);
  return value;
}
