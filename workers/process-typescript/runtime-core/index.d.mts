export type EvidenceField = "taskId" | "attemptId" | "attemptNo" | "role" | "runtime" | "event" | "seq" | "time"
  | "status" | "code" | "category" | "phase" | "reason" | "callId" | "parentCallId" | "tool" | "operation"
  | "sourceId" | "path" | "digest" | "bytes" | "count" | "inputBytes" | "outputBytes" | "inputTokens" | "outputTokens"
  | "cachedTokens" | "usageKnown" | "usageSource" | "durationMs" | "complete" | "truncated" | "mode" | "sideEffect"
  | "resultRevision" | "publicationRef" | "baseRevision" | "workspaceKey" | "readOnly" | "maxBytes" | "maxTurns"
  | "deadlineMs" | "model" | "effort" | "sourceIds" | "summaryCalls" | "processTree" | "reportStatus"
  | "retentionDays" | "verdict" | "capability" | "unknown" | "revision";
export type Metadata = Partial<Record<EvidenceField, string | number | boolean | null | string[] | undefined>>
  & { versions?: Record<string, string> };
export interface ResourceRequest { mode: "IS" | "IX" | "S" | "X"; files?: { key: string; mode: "S" | "X" }[] }
export class RuntimeFault extends Error { constructor(code: string, category?: string, fatal?: boolean); code: string; category: string; fatal: boolean }
export function checkSignal(signal?: AbortSignal): void;
export function errorCode(error: unknown): string;
export function failureCategory(error: unknown, phase?: string): string;
export function utf8Bytes(value: unknown): number;
export interface BoundedText { text: string; complete: boolean; truncated: boolean; originalBytes: number; originalLines: number; mode: string }
/** maxBytes is an integer >=128; maxLines is an integer >=3. Both include the truncation marker. */
export function boundedText(value: unknown, options?: { maxBytes?: number; maxLines?: number; mode?: string }): BoundedText;
export function toolOutput(content: unknown, options?: { maxBytes?: number; maxLines?: number; mode?: string; complete?: boolean; status?: string; data?: unknown }): string;
export function checkedJson(text: string, maxBytes: number, validate: (value: unknown) => boolean): unknown;
export function sha256(value: string | Uint8Array): string;
export function isInside(root: string, target: string, allowRoot?: boolean): boolean;
export function safePath(root: string, relative: string, write?: boolean): Promise<string>;
export class ResourceCoordinator {
  constructor(options?: { maxActive?: number; maxQueue?: number });
  static forWorkspace(cwd: string, options?: { maxActive?: number; maxQueue?: number }): Promise<ResourceCoordinator>;
  static fileKey(file: string): string;
  run<T>(request: ResourceRequest, signal: AbortSignal | undefined, operation: () => Promise<T>): Promise<T>;
  settled(): Promise<void>;
}
export class EvidenceRecorder {
  directory: string;
  static open(root: string, identity: Metadata, options?: { maxBytes?: number; forbiddenRoots?: string[] }): Promise<EvidenceRecorder>;
  static prune(root: string, now?: number): Promise<void>;
  record(event: string, details?: Metadata): Promise<void>;
  assertHealthy(): void;
  finish(status: string): Promise<void>;
}
export function commandEnvironment(extra?: Record<string, string>): Record<string, string>;
export function runCommand(executable: string, args: string[], options?: { cwd?: string; signal?: AbortSignal; timeoutMs?: number; maxBytes?: number; exact?: boolean; env?: Record<string, string>; allowFailure?: boolean }): Promise<{ exitCode: number; stdout: string; stderr: string; complete: boolean; processTree: string }>;
export function planEdits(original: string, edits: { oldText: string; newText: string }[]): { text: string; diff: string; count: number };
export function atomicWrite(file: string, text: string, signal: AbortSignal, expectedDigest?: string): Promise<void>;
export function editFile(file: string, edits: { oldText: string; newText: string }[], signal: AbortSignal, options?: { maxFileBytes?: number; maxDiffBytes?: number }): Promise<BoundedText & { count: number; digest: string }>;
export interface ToolDefinition {
  name: string; write?: boolean; shell?: boolean; file?: boolean; compose?: boolean; resource?: ResourceRequest;
  validate?: (args: any) => boolean;
  run: (args: any, context: { file?: string; signal: AbortSignal; callId: string; host: ToolHost }) => Promise<string | { content: string; complete?: boolean; status?: string; mode?: string; data?: unknown }>;
}
export class ToolHost {
  constructor(config: { cwd: string; readOnly: boolean; allowShell: boolean; signal: AbortSignal; recorder: EvidenceRecorder; coordinator: ResourceCoordinator; abort?: (reason: string) => void; maxOutputBytes?: number; toolTimeoutMs?: number });
  execute(definition: ToolDefinition, args: unknown, details?: { callId?: string; parentCallId?: string }): Promise<string>;
  settled(timeoutMs?: number): Promise<boolean>;
  assertSuccessful(): void;
}
export class ContextAssembler {
  constructor(config?: { maxInputBytes?: number; recorder?: EvidenceRecorder; signal?: AbortSignal });
  maxInputBytes: number;
  sources: Map<string, { sourceId: string; path: string; digest: string; bytes: number }>;
  source(path: string, text: string, kind?: string): string;
  assemble(parts: (string | undefined | null)[]): string;
  record(text: string): Promise<void>;
}
export function readSkillPackage(root: string, skill: string, assembler?: ContextAssembler): string;
export function requestBytes(request: any): number;
export class HistoryCompactor {
  constructor(config?: { enabled?: boolean; thresholdBytes?: number; summaryMaxTokens?: number; maxSourceBytes?: number; recorder?: EvidenceRecorder; signal?: AbortSignal });
  enabled: boolean;
  source(id: string): string;
  sourcePage(id: string, offset?: number, maxBytes?: number): { text: string; offset: number; nextOffset: number; totalBytes: number; complete: boolean };
  prepare<T>(request: T, summarize: (text: string, sourceId: string, maxTokens: number) => Promise<string>, maxInputBytes: number): Promise<T>;
  clear(): void;
}
export class AttemptRunner {
  /** Supply recorder here or assign it before step; missing evidence rejects before running the operation. */
  constructor(config?: { timeoutMs?: number; parentSignal?: AbortSignal; settleTimeoutMs?: number; recorder?: EvidenceRecorder });
  signal: AbortSignal; recorder: EvidenceRecorder; resourceUncertain: boolean;
  initializeEvidence(root: string, identity: Metadata, options?: { maxBytes?: number; forbiddenRoots?: string[] }): Promise<EvidenceRecorder>;
  cancel(reason?: string): void;
  step<T>(phase: string, operation: () => Promise<T>): Promise<T>;
  settled(timeoutMs?: number): Promise<boolean>;
  dispose(): void;
}
export interface WorkspaceAssignment { repositoryRef: string; baseRevision: string | null; workspaceKey: string; branchName: string | null; workspaceId?: string; workspaceName?: string; workspaceRoot?: string; repositoryPath?: string; mode?: "DIRECT" | "GIT_WORKTREE" }
export interface WorkspaceResult { resultRevision: string; changedFiles: string[]; publicationRef: string | null }
export class WorkspaceSession {
  cwd: string; assignment: WorkspaceAssignment | null; coordinator: ResourceCoordinator;
  static materialize(cfg: any, task: any, attempt: AttemptRunner): Promise<WorkspaceSession>;
  beforeExecution(readOnly: boolean): Promise<void>;
  validateReadOnly(readOnly: boolean): Promise<void>;
  finalize(task: any, readOnly: boolean): Promise<WorkspaceResult | null>;
  cleanup(): Promise<void>;
}
export function workspaceSnapshot(cwd: string, signal?: AbortSignal): Promise<string>;
export class ProtocolError extends Error { constructor(status: number); status: number }
export class ControlPlaneClient {
  constructor(cfg: any, runtimeType: string);
  register(): Promise<void>; workerHeartbeat(): Promise<void>; drain(): Promise<void>; offline(): Promise<void>;
  claim(): Promise<any | null>;
  start(task: any, selection?: any, signal?: AbortSignal): Promise<void>;
  heartbeat(task: any, sessionId: string | null): Promise<void>;
  fail(task: any, sessionId: string | null, reason?: string | null, category?: string): Promise<void>;
  complete(task: any, sessionId: string | null, workspace: WorkspaceResult | null, report: unknown, signal?: AbortSignal): Promise<void>;
}
export interface AttemptExecution { task: any; workspace: WorkspaceSession; profile: any; prompt: string; context: ContextAssembler; attempt: AttemptRunner; selection: any; onSession: (id: string) => void }
export interface AttemptAdapter {
  runtime: string; versions?: Record<string, string>;
  select(task: any): any; profile(task: any): any; prompt(task: any, context: ContextAssembler): string;
  execute(input: AttemptExecution): Promise<string>;
  report(resultKind: string, response: string): unknown;
  digest?(text: string): string;
  settled?(timeoutMs?: number): Promise<void | boolean>; assertSuccessful?(): void; clear?(): void;
}
export interface AttemptOutcome { status: string; sessionId: string | null; workspaceResult?: WorkspaceResult | null; evidence?: string; resourceUncertain: boolean }
export function runClaimedAttempt(cfg: any, client: any, task: any, adapter: AttemptAdapter): Promise<AttemptOutcome>;
export function runWorkerSlot(options: {
  cfg: any; client: any; createAdapter(task: any): AttemptAdapter; shouldStop(): boolean;
  onOutcome?(outcome: AttemptOutcome, task: any): void;
  onUncertain?(outcome: AttemptOutcome, task: any): void | Promise<void>;
  onError?(error: unknown): void;
}): Promise<void>;
export function validateModelResponse(response: any, raw?: any, request?: any): void;
export function guardedModel<T>(model: T, config: { recorder: EvidenceRecorder; signal: AbortSignal; history?: HistoryCompactor; maxInputBytes?: number }): T;
export interface RuntimeSettings { taskTimeoutMs: number; cleanupTimeoutMs: number; gitTimeoutMs: number; maxInputBytes: number; evidenceMaxBytes: number; evidenceRoot?: string; forceSignal?: AbortSignal }
export function runtimeSettings(): RuntimeSettings;
export function positiveSetting(name: string, fallback: number | string): number;
export class AiwAdapter {
  entries: { id: string }[];
  static load(catalogPath: string | undefined, cwd: string): Promise<AiwAdapter>;
  query(id: string, input: { number?: number }, signal: AbortSignal): Promise<{ content: string; complete: boolean; data: unknown }>;
}
