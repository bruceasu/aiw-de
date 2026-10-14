import { createHash, randomUUID } from "node:crypto";
import { lstat, mkdir, appendFile, writeFile, readFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import { RuntimeFault } from "./errors.mjs";
import { isInside } from "./resources.mjs";

const owner = "aiw.worker.attempt.v1";
const keys = new Set(("taskId attemptId attemptNo role runtime versions event seq time status code category phase reason "
  + "callId parentCallId tool operation sourceId path digest bytes count inputBytes outputBytes inputTokens outputTokens "
  + "cachedTokens usageKnown usageSource durationMs complete truncated mode sideEffect resultRevision publicationRef "
  + "baseRevision workspaceKey repositoryRef readOnly maxBytes maxTurns deadlineMs warningMs model effort sourceIds summaryCalls "
  + "processTree reportStatus retentionDays verdict capability unknown revision exitCode").split(" "));
function metadata(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new RuntimeFault("EVIDENCE_SCHEMA_INVALID");
  const result = {};
  for (const [key, item] of Object.entries(value)) {
    if (!keys.has(key)) throw new RuntimeFault("EVIDENCE_FIELD_FORBIDDEN");
    if (item === undefined) continue;
    if (typeof item === "number" && !Number.isFinite(item)) throw new RuntimeFault("EVIDENCE_VALUE_INVALID");
    if (item === null || typeof item === "boolean" || typeof item === "number") result[key] = item;
    else if (typeof item === "string" && item.length <= 4096) result[key] = item;
    else if (Array.isArray(item) && item.length <= 256 && item.every(x => typeof x === "string" && x.length <= 4096)) result[key] = item;
    else if (key === "versions" && item && typeof item === "object"
      && Object.values(item).every(x => typeof x === "string" && x.length < 128)) result[key] = item;
    else throw new RuntimeFault("EVIDENCE_VALUE_INVALID");
  }
  return result;
}

async function regularDirectory(target) {
  const absolute = path.resolve(target);
  let cursor = path.parse(absolute).root;
  for (const part of absolute.slice(cursor.length).split(path.sep).filter(Boolean)) {
    cursor = path.join(cursor, part);
    try {
      const stat = await lstat(cursor);
      if (!stat.isDirectory() || stat.isSymbolicLink()) throw new RuntimeFault("EVIDENCE_DIRECTORY_UNSAFE", "CONFIGURATION");
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
      try { await mkdir(cursor, { mode: 0o700 }); }
      catch (creationError) {
        if (creationError.code !== "EEXIST") throw creationError;
        const stat = await lstat(cursor);
        if (!stat.isDirectory() || stat.isSymbolicLink()) throw new RuntimeFault("EVIDENCE_DIRECTORY_UNSAFE", "CONFIGURATION");
      }
    }
  }
  return absolute;
}

export const sha256 = value => createHash("sha256").update(value).digest("hex");

export class EvidenceRecorder {
  #tail = Promise.resolve();
  #failure;
  #bytes = 0;
  #seq = 0;
  constructor(directory, identity, maxBytes) {
    this.directory = directory;
    this.identity = identity;
    this.maxBytes = maxBytes;
  }
  static async open(root, identity, { maxBytes = 4 * 1024 * 1024, forbiddenRoots = [] } = {}) {
    identity = metadata(identity);
    root = path.resolve(root);
    if (forbiddenRoots.filter(Boolean).some(workspace => isInside(workspace, root, true))) {
      throw new RuntimeFault("EVIDENCE_MUST_BE_OUTSIDE_WORKSPACE", "CONFIGURATION");
    }
    await regularDirectory(root);
    const datetime = new Date().toISOString().replaceAll(":", "-");
    const directory = path.join(root, `${datetime}_${randomUUID()}`);
    await mkdir(directory, { mode: 0o700 });
    const header = JSON.stringify({ owner, ...identity });
    if (Buffer.byteLength(header) >= maxBytes) throw new RuntimeFault("EVIDENCE_CAPACITY_EXCEEDED");
    await writeFile(path.join(directory, "owner.json"), header, { flag: "wx", mode: 0o600 });
    const recorder = new EvidenceRecorder(directory, identity, maxBytes);
    recorder.#bytes = Buffer.byteLength(header);
    await recorder.record("attempt-started", { status: "running", retentionDays: 30 });
    return recorder;
  }
  record(event, details = {}) {
    const operation = this.#tail.then(async () => {
      this.assertHealthy();
      const line = JSON.stringify({ ...this.identity, ...metadata(details), event, seq: ++this.#seq, time: new Date().toISOString() }) + "\n";
      const bytes = Buffer.byteLength(line);
      if (this.#bytes + bytes > this.maxBytes) throw new RuntimeFault("EVIDENCE_CAPACITY_EXCEEDED");
      await appendFile(path.join(this.directory, "events.jsonl"), line, { mode: 0o600 });
      this.#bytes += bytes;
    });
    this.#tail = operation.catch(() => { this.#failure = new RuntimeFault("CRITICAL_EVIDENCE_FAILED"); });
    return operation.catch(error => { this.#failure ??= new RuntimeFault("CRITICAL_EVIDENCE_FAILED"); throw error; });
  }
  assertHealthy() { if (this.#failure) throw this.#failure; }
  async finish(status) {
    await this.record("attempt-terminal", { status });
    const marker = JSON.stringify({ owner, status, time: new Date().toISOString() });
    if (this.#bytes + Buffer.byteLength(marker) > this.maxBytes) { this.#failure = new RuntimeFault("EVIDENCE_CAPACITY_EXCEEDED"); throw this.#failure; }
    await writeFile(path.join(this.directory, "terminal.json"), marker, { flag: "wx", mode: 0o600 })
      .catch(() => { this.#failure = new RuntimeFault("CRITICAL_EVIDENCE_FAILED"); throw this.#failure; });
    this.#bytes += Buffer.byteLength(marker);
  }
  static async prune(root, now = Date.now()) {
    root = path.resolve(root);
    // Only owned terminal attempt directories. Never prune an unknown/active directory.
    const entries = await readdir(root, { withFileTypes: true }).catch(error => { if (error.code === "ENOENT") return []; throw error; });
    for (const entry of entries) {
      if (!entry.isDirectory() || entry.isSymbolicLink()) continue;
      const directory = path.resolve(root, entry.name);
      if (!isInside(root, directory)) throw new RuntimeFault("RETENTION_PATH_INVALID");
      try {
        for (const name of ["owner.json", "terminal.json"]) {
          const stat = await lstat(path.join(directory, name));
          if (!stat.isFile() || stat.isSymbolicLink()) throw new RuntimeFault("RETENTION_MARKER_UNSAFE");
        }
        const identity = JSON.parse(await readFile(path.join(directory, "owner.json"), "utf8"));
        const terminal = JSON.parse(await readFile(path.join(directory, "terminal.json"), "utf8"));
        if (identity.owner === owner && terminal.owner === owner && Number.isFinite(Date.parse(terminal.time))
          && now - Date.parse(terminal.time) > 30 * 86400000) await rm(directory, { recursive: true });
      } catch (error) { if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) throw error; }
    }
  }
}
