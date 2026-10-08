// Synthetic-only, capacity-bounded diagnostics; never reads host credentials or source bodies.
import { AsyncLocalStorage } from 'node:async_hooks';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';

const context = new AsyncLocalStorage();
const fixtures = new Map();
const ownedRoot = process.env.FD005_RUN_ROOT;
const resultsRoot = process.env.FD005_RESULT_ROOT;
if (!ownedRoot || !resultsRoot) throw new Error('FD005_AUTHORIZED_RUN_REQUIRED');
const diagnosticRoot = path.join(resultsRoot, 'diagnostics');
const maxFileBytes = 2 * 1024 * 1024, maxTotalBytes = 8 * 1024 * 1024;
const permittedFields = new Set(('taskId attemptId attemptNo role runtime event seq time status code category phase reason callId parentCallId tool operation sourceId path digest bytes count inputBytes outputBytes inputTokens outputTokens cachedTokens usageKnown usageSource durationMs complete truncated mode sideEffect resultRevision publicationRef baseRevision workspaceKey readOnly maxBytes maxTurns deadlineMs model effort sourceIds summaryCalls processTree reportStatus retentionDays verdict capability unknown revision versions resourceUncertain').split(' '));
export const metadataDigest = value => createHash('sha256').update(String(value)).digest('hex');
export const currentBehavior = () => context.getStore()?.id || 'synthetic-unscoped';
export const withBehavior = (id, operation) => context.run({ id }, operation);
const inside = (root, target) => { const relative = path.relative(path.resolve(root), path.resolve(target)); return relative === '' || (!relative.startsWith('..' + path.sep) && relative !== '..' && !path.isAbsolute(relative)); };

export function appendDiagnostic(name, fields) {
  if (!/^[a-z][a-z0-9-]{0,48}\.jsonl$/.test(name)) throw new Error('FD005_DIAGNOSTIC_FILENAME_INVALID');
  fs.mkdirSync(diagnosticRoot, { recursive: true });
  const text = JSON.stringify({ syntheticTestOnly: true, behaviorId: currentBehavior(), ...fields }) + '\n';
  if (Buffer.byteLength(text) > 128 * 1024) throw new Error('FD005_DIAGNOSTIC_ROW_LIMIT');
  const filename = path.join(diagnosticRoot, name);
  const existing = fs.existsSync(filename) ? fs.statSync(filename).size : 0;
  let total = 0;
  for (const entry of fs.readdirSync(diagnosticRoot, { withFileTypes: true })) if (entry.isFile()) total += fs.statSync(path.join(diagnosticRoot, entry.name)).size;
  if (existing + Buffer.byteLength(text) > maxFileBytes || total + Buffer.byteLength(text) > maxTotalBytes) throw new Error('FD005_DIAGNOSTIC_CAPACITY_EXCEEDED');
  fs.appendFileSync(filename, text);
}

export function registerFixture(fixture) {
  if (!inside(ownedRoot, fixture.directory) || !inside(fixture.directory, fixture.workspace)) throw new Error('FD005_FOREIGN_FIXTURE_REFUSED');
  const id = currentBehavior();
  if (!fixtures.has(id)) fixtures.set(id, []);
  fixtures.get(id).push(fixture);
}
function safeValue(value) {
  if (value === null || typeof value === 'boolean') return value;
  if (typeof value === 'number') return Number.isFinite(value) ? value : '[INVALID_NUMBER]';
  if (typeof value === 'string') {
    if (/SYNTHETIC_API_KEY_ONLY|SYNTHETIC_LEASE_TEST_ONLY/.test(value)) return '[REDACTED_SYNTHETIC_TEST_SECRET]';
    return value.length <= 4096 ? value : '[OVERSIZE_VALUE_DIGEST:' + metadataDigest(value) + ']';
  }
  if (Array.isArray(value)) return value.slice(0,256).map(item => typeof item === 'string' ? safeValue(item) : '[INVALID_ITEM]');
  return undefined;
}
function metadataOnly(value) {
  const metadata = {}, discardedFields = [];
  for (const [key, item] of Object.entries(value || {})) {
    if (!permittedFields.has(key)) { discardedFields.push(key.slice(0,128)); continue; }
    if (key === 'versions' && item && typeof item === 'object' && !Array.isArray(item)) {
      metadata.versions = Object.fromEntries(Object.entries(item).slice(0,32).map(([name,version]) => [name.slice(0,128), safeValue(version)]));
    } else { const safe = safeValue(item); if (safe !== undefined) metadata[key] = safe; else discardedFields.push(key); }
  }
  return { metadata, discardedFields };
}
export async function evidenceRows(directory) {
  if (!inside(ownedRoot, directory)) throw new Error('FD005_FOREIGN_EVIDENCE_REFUSED');
  const rows = [];
  async function visit(current, depth = 0) {
    if (depth > 8) throw new Error('FD005_EVIDENCE_DEPTH_LIMIT');
    const entries = await fsp.readdir(current, { withFileTypes: true }).catch(error => { if (['ENOENT','ENOTDIR'].includes(error.code)) return []; throw error; });
    for (const entry of entries.sort((a,b) => a.name.localeCompare(b.name))) {
      const filename = path.join(current, entry.name);
      if (entry.isSymbolicLink()) throw new Error('FD005_EVIDENCE_LINK_REFUSED');
      if (entry.isDirectory()) { await visit(filename, depth + 1); continue; }
      if (!entry.isFile() || !/\.jsonl?$/.test(entry.name)) continue;
      const resolved = await fsp.realpath(filename);
      if (!inside(ownedRoot,resolved)) throw new Error('FD005_FOREIGN_EVIDENCE_REFUSED');
      const stat = await fsp.stat(resolved);
      if (stat.size > 4 * 1024 * 1024) throw new Error('FD005_EVIDENCE_FILE_LIMIT');
      const text = await fsp.readFile(resolved, 'utf8');
      const values = entry.name.endsWith('.jsonl') ? text.split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line)) : [JSON.parse(text)];
      for (const [index,value] of values.entries()) {
        if (rows.length >= 2048) throw new Error('FD005_EVIDENCE_RECORD_LIMIT');
        rows.push({ file: path.relative(directory,filename).replaceAll('\\','/'), row: index + 1, ...metadataOnly(value) });
      }
    }
  }
  await visit(directory);
  return rows;
}
export async function snapshotBehaviorMetadata() {
  for (const fixture of fixtures.get(currentBehavior()) || []) {
    const rows = await evidenceRows(path.join(fixture.directory,'evidence'));
    const fixtureName = path.basename(fixture.directory);
    for (let offset = 0; offset < rows.length; offset += 64) appendDiagnostic('attempt-metadata.jsonl', { fixtureName, offset, records: rows.slice(offset, offset+64) });
    if (!rows.length) appendDiagnostic('attempt-metadata.jsonl', { fixtureName, records: [], note: 'EVIDENCE_ABSENT_OR_REMOVED_BY_RETENTION_CASE' });
  }
}
export async function verifyAll(checks) {
  const errors = [], results = [];
  for (const [id, operation] of Object.entries(checks)) {
    try { await operation(); results.push({ id, outcome: 'passed' }); }
    catch (error) { errors.push(error); results.push({ id, outcome: 'failed', errorCode: error.code || error.name || 'SYNTHETIC_ASSERTION', messageDigest: metadataDigest(error.message || '') }); }
  }
  appendDiagnostic('assertions.jsonl', { checks: results });
  if (errors.length) throw new AggregateError(errors, 'FD005_ASSERTIONS_FAILED:' + results.filter(item => item.outcome === 'failed').map(item => item.id).join(','));
}

