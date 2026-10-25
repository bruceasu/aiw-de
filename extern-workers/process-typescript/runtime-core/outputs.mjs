import { lstat, readFile } from "node:fs/promises";
import { RuntimeFault } from "./errors.mjs";
import { safePath } from "./resources.mjs";
import { sha256 } from "./evidence.mjs";
import { workspaceSnapshot } from "./workspace.mjs";

const maxFile = 4 * 1024 * 1024;
const maxBundle = 8 * 1024 * 1024;

function outputPath(value) {
  if (typeof value !== "string" || value.length > 480 || !/^[\p{L}\p{N}\p{M} ._/-]+$/u.test(value)
    || value.split("/").some(part => !part || part.trim() !== part || part.endsWith(".")
      || Buffer.byteLength(part, "utf8") > 255 || [".", "..", ".git", ".aiw"].includes(part.toLowerCase()))) {
    throw new RuntimeFault("OUTPUT_PATH_INVALID", "OUTPUT_INVALID");
  }
  return value;
}

/** Collect only declared files before workspace cleanup; read-only roles may return inline documents. */
export async function collectOutputFiles(report, workspace, signal) {
  const files = report.outputFiles ?? [];
  if (!Array.isArray(files) || files.length > 16) throw new RuntimeFault("OUTPUT_FILE_LIMIT", "OUTPUT_INVALID");
  const result = [];
  const seen = new Set();
  let total = Buffer.byteLength(report.summary ?? "", "utf8") * 2;
  for (const file of files) {
    signal.throwIfAborted();
    const name = outputPath(file?.path);
    if (seen.has(name.normalize("NFC").toLowerCase()) || !["DOCUMENT", "ATTACHMENT"].includes(file.kind)
      || (typeof file.content === "string") === (typeof file.sourcePath === "string")) {
      throw new RuntimeFault("OUTPUT_FILE_INVALID", "OUTPUT_INVALID");
    }
    seen.add(name.normalize("NFC").toLowerCase());
    let content;
    if (typeof file.content === "string") {
      if (Buffer.byteLength(file.content, "utf8") > maxFile) throw new RuntimeFault("OUTPUT_FILE_TOO_LARGE", "OUTPUT_INVALID");
      content = Buffer.from(file.content, "utf8");
    } else {
      const source = await safePath(workspace, outputPath(file.sourcePath));
      const info = await lstat(source);
      if (!info.isFile() || info.isSymbolicLink() || info.nlink > 1 || info.size > maxFile) throw new RuntimeFault("OUTPUT_SOURCE_INVALID", "OUTPUT_INVALID");
      content = await readFile(source, { signal });
    }
    total += content.length;
    if (content.length > maxFile || total > maxBundle) throw new RuntimeFault("OUTPUT_CAPACITY_EXCEEDED", "OUTPUT_INVALID");
    result.push({ path: name, kind: file.kind, contentBase64: content.toString("base64") });
  }
  return result;
}

export async function publicationSnapshot(task, cwd, signal) {
  const publication = task.outputStore?.publication;
  return publication ? workspaceSnapshot(cwd, signal, outputPath(publication.targetPath)) : null;
}

export async function validatePublication(task, inputs, before, cwd, signal) {
  const publication = task.outputStore?.publication;
  if (!publication) return;
  const expected = inputs.find(file => file.id === publication.fileId);
  if (!expected || expected.kind === "ATTACHMENT") throw new RuntimeFault("PUBLICATION_SOURCE_MISSING", "CONFIGURATION");
  const target = await safePath(cwd, outputPath(publication.targetPath));
  const info = await lstat(target);
  if (!info.isFile() || info.nlink > 1 || info.size > maxFile
      || sha256(await readFile(target, { signal })) !== expected.sha256
      || await publicationSnapshot(task, cwd, signal) !== before) {
    throw new RuntimeFault("PUBLICATION_CONTENT_INVALID", "OUTPUT_INVALID");
  }
}

/** CP pins versions and authorizes reads with the current task lease, including publication source inputs. */
export async function readOutputInputs(client, task, context, signal) {
  const refs = task.outputStore?.inputs ?? [];
  if (!Array.isArray(refs) || refs.length > 64) throw new RuntimeFault("OUTPUT_INPUT_LIMIT", "CONFIGURATION");
  const inputs = [];
  let total = 0;
  for (const ref of refs) {
    const files = await client.readOutputs(task, ref.attemptId, signal);
    if (!Array.isArray(files) || files.length !== ref.files.length) throw new RuntimeFault("OUTPUT_INPUT_INVALID", "OUTPUT_INVALID");
    for (const expected of ref.files) {
      const file = files.find(candidate => candidate.file?.id === expected.id);
      if (!file || typeof file.contentBase64 !== "string" || file.contentBase64.length > maxFile * 4 / 3 + 8) throw new RuntimeFault("OUTPUT_INPUT_INVALID", "OUTPUT_INVALID");
      const content = Buffer.from(file.contentBase64, "base64");
      total += content.length;
      if (total > maxBundle || content.length !== expected.bytes || sha256(content) !== expected.sha256) throw new RuntimeFault("OUTPUT_INPUT_HASH_MISMATCH", "OUTPUT_INVALID");
      let text;
      try { text = new TextDecoder("utf-8", { fatal: true }).decode(content); }
      catch { text = null; }
      const input = { taskId: ref.taskId, attemptId: ref.attemptId, ...expected,
        ...(text === null ? { contentBase64: file.contentBase64 } : { content: text }) };
      context.source(`output:${expected.id}`, JSON.stringify(input), "control-plane-output");
      inputs.push(input);
    }
  }
  return inputs;
}
