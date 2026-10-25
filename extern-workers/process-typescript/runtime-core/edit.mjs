import { readFile, stat, writeFile, rename, unlink } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import path from "node:path";
import { RuntimeFault, checkSignal } from "./errors.mjs";
import { sha256 } from "./evidence.mjs";
import { boundedText } from "./output.mjs";

function normalized(text) {
  const indices = [];
  let value = "";
  for (let index = 0; index < text.length; index++) {
    indices.push(index);
    if (text[index] === "\r" && text[index + 1] === "\n") { value += "\n"; index++; }
    else value += text[index];
  }
  indices.push(text.length);
  return { value, indices };
}

export function planEdits(original, edits) {
  if (!Array.isArray(edits) || !edits.length || edits.length > 128) throw new RuntimeFault("EDIT_COUNT_INVALID", "TOOL_ERROR", false);
  const bom = original.startsWith("\uFEFF") ? "\uFEFF" : "";
  const body = original.slice(bom.length);
  const baseline = normalized(body);
  const newline = body.includes("\r\n") ? "\r\n" : "\n";
  const regions = edits.map(edit => {
    if (typeof edit.oldText !== "string" || !edit.oldText || typeof edit.newText !== "string") throw new RuntimeFault("EDIT_ARGUMENT_INVALID", "TOOL_ERROR", false);
    const oldText = edit.oldText.replace(/\r\n/g, "\n");
    const index = baseline.value.indexOf(oldText);
    if (index < 0 || baseline.value.indexOf(oldText, index + 1) >= 0) throw new RuntimeFault("EDIT_MATCH_NOT_UNIQUE", "TOOL_ERROR", false);
    return { start: baseline.indices[index], end: baseline.indices[index + oldText.length], oldText: body.slice(baseline.indices[index], baseline.indices[index + oldText.length]),
      newText: edit.newText.replace(/\r\n/g, "\n").replace(/\n/g, newline) };
  }).sort((a, b) => a.start - b.start);
  for (let index = 1; index < regions.length; index++) {
    if (regions[index].start < regions[index - 1].end) throw new RuntimeFault("EDIT_REGIONS_OVERLAP", "TOOL_ERROR", false);
  }
  let updated = body;
  for (const region of [...regions].reverse()) updated = updated.slice(0, region.start) + region.newText + updated.slice(region.end);
  const diff = regions.map(region => `@@ offset ${region.start} @@\n-${region.oldText}\n+${region.newText}`).join("\n");
  return { text: bom + updated, diff, count: regions.length };
}

export async function atomicWrite(file, text, signal, expectedDigest) {
  checkSignal(signal);
  const temporary = path.join(path.dirname(file), `.aiw-edit-${randomUUID()}.tmp`);
  let mode = 0o600;
  try { mode = (await stat(file)).mode & 0o777; } catch (error) { if (error.code !== "ENOENT") throw error; }
  try {
    await writeFile(temporary, text, { encoding: "utf8", flag: "wx", mode, signal });
    checkSignal(signal);
    if (expectedDigest !== undefined && sha256(await readFile(file, { signal })) !== expectedDigest) throw new RuntimeFault("EDIT_BASELINE_CHANGED", "TOOL_ERROR", false);
    checkSignal(signal);
    await rename(temporary, file);
  } finally { await unlink(temporary).catch(error => { if (error.code !== "ENOENT") throw error; }); }
}

export async function editFile(file, edits, signal, { maxFileBytes = 2 * 1024 * 1024, maxDiffBytes = 12000 } = {}) {
  const info = await stat(file);
  if (info.size > maxFileBytes) throw new RuntimeFault("EDIT_FILE_TOO_LARGE", "TOOL_ERROR", false);
  const bytes = await readFile(file, { signal });
  let original;
  try { original = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes); }
  catch { throw new RuntimeFault("EDIT_FILE_NOT_UTF8", "TOOL_ERROR", false); }
  const plan = planEdits(original, edits);
  if (Buffer.byteLength(plan.text) > maxFileBytes) throw new RuntimeFault("EDIT_RESULT_TOO_LARGE", "TOOL_ERROR", false);
  await atomicWrite(file, plan.text, signal, sha256(bytes));
  const diff = boundedText(plan.diff, { maxBytes: maxDiffBytes, mode: "middle" });
  return { ...diff, count: plan.count, digest: sha256(plan.text) };
}
