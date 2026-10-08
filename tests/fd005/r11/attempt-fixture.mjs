import { assert, fs, path, deferred, within, outcome, wait } from './harness.mjs';
import { runtime, apiFixture, createApiAttemptAdapter, executeFixture } from './api-fixture.mjs';
import { gitFixture } from './workspace-fixture.mjs';
import { appendDiagnostic, evidenceRows } from './diagnostics.mjs';

export function controlledAdapter(fixture, { blockExecution = false, blockSettled = false, uncertainTermination = false, settledReturnsFalse = false } = {}) {
  const started = deferred(), settledStarted = deferred(), release = deferred();
  const state = { executeEnded: false, resourcesEnded: false, clearCalls: 0, clearBeforeEnd: false, probes: 0 };
  const syntheticRuntime = {
    execute: async input => {
      state.input = input;
      if (blockExecution) {
        state.executePromise = input.workspace.coordinator.run({ mode: 'X' }, input.attempt.signal, () => {
          state.ioBodyPromise = (async () => {
          started.resolve(input);
          // Controlled uncooperative transaction: no OS/service is being kept alive.
          await release.promise;
          try { await fs.writeFile(path.join(input.workspace.cwd, 'synthetic-io-ended.txt'), 'SYNTHETIC_IO_END'); }
          finally { state.resourcesEnded = true; }
          return fixture.example.finalResponse;
          })();
          if (uncertainTermination) throw new runtime.RuntimeFault('RESOURCE_TERMINATION_UNCERTAIN','TOOL_ERROR',true);
          return state.ioBodyPromise;
        });
        const value = await state.executePromise; state.executeEnded = true; return value;
      }
      state.executeEnded = true;
      started.resolve(input);
      return fixture.example.finalResponse;
    },
    settled: async timeoutMs => {
      appendDiagnostic('controlled-io.jsonl', { phase: 'settled-called', timeoutMs, blockExecution, blockSettled });
      settledStarted.resolve();
      if (settledReturnsFalse) {
        state.settlePromise = release.promise.then(() => { state.resourcesEnded = true; return true; });
        return false;
      }
      if (blockSettled) {
        state.settlePromise = release.promise.then(() => { state.resourcesEnded = true; return true; });
        return state.settlePromise;
      }
      if (state.ioBodyPromise) { await state.ioBodyPromise; return state.resourcesEnded; }
      if (state.executePromise) { await state.executePromise; return state.resourcesEnded; }
      state.resourcesEnded = true; return true;
    },
    assertSuccessful: () => {},
    clear: () => { state.clearCalls++; state.clearBeforeEnd ||= !state.resourcesEnded; appendDiagnostic('controlled-io.jsonl', { phase: 'clear', executeEnded: state.executeEnded, resourcesEnded: state.resourcesEnded }); },
  };
  const adapter = createApiAttemptAdapter({ role: 'coder', skillsRoot: fixture.skillsRoot }, syntheticRuntime, { fixture: 'synthetic-controlled-resource' });
  async function finish() {
    release.resolve();
    const pending = [state.executePromise,state.settlePromise,state.ioBodyPromise].filter(Boolean);
    const ended = await outcome(within(Promise.allSettled(pending),1500));
    if (ended.rejected) {
      await fs.appendFile(path.join(process.env.FD005_RESULT_ROOT,'resource-uncertain.jsonl'), JSON.stringify({ syntheticTestOnly: true, code: 'CONTROLLED_RESOURCE_RECOVERY_NOT_CONFIRMED' }) + '\n');
      throw ended.error;
    }
  }
  return { adapter, state, started, settledStarted, release, finish };
}
export async function boundedAttempt(id, { activeIO = false, uncertainTermination = false, settledReturnsFalse = false, cleanupMs = 80 } = {}) {
  const controller = new AbortController();
  let fixture = await apiFixture(id,'coder',{ config: { taskTimeoutMs: 6000, cleanupTimeoutMs: cleanupMs, gitTimeoutMs: 1000,
    ...(activeIO ? { forceSignal: controller.signal } : {}) } });
  // Both cases use an actual owned Git worktree, so premature task-workspace deletion is observable.
  fixture = await gitFixture(id,false,fixture);
  const controlled = controlledAdapter(fixture,{ blockExecution: activeIO, blockSettled: !activeIO, uncertainTermination, settledReturnsFalse });
  fixture.adapter = controlled.adapter;
  const pending = outcome(executeFixture(fixture));
  let result, boundary, probe;
  try {
    const input = await within(controlled.started.promise);
    if (activeIO) controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
    else await within(controlled.settledStarted.promise);
    const from = Date.now();
    result = await within(pending,1200);
    const rows = await evidenceRows(fixture.config.evidenceRoot);
    const worktreeExists = (await fs.stat(input.workspace.cwd).catch(() => null))?.isDirectory() === true;
    boundary = { returnedWithinMs: Date.now() - from, worktreeExists, clearCalls: controlled.state.clearCalls,
      clearBeforeEnd: controlled.state.clearBeforeEnd, resourceEnded: controlled.state.resourcesEnded, rows };
    appendDiagnostic('controlled-io.jsonl', { phase: 'bounded-return', activeIO, returnedWithinMs: boundary.returnedWithinMs,
      resourceUncertain: result.value?.resourceUncertain, status: result.value?.status, worktreeExists,
      clearCalls: boundary.clearCalls, clearBeforeEnd: boundary.clearBeforeEnd, resourceEnded: boundary.resourceEnded });
    if (activeIO) {
      probe = outcome(input.workspace.coordinator.run({ mode: 'X' },undefined,async () => { controlled.state.probes++; }));
      if (uncertainTermination) boundary.probeResult = await outcome(within(probe,300));
      else { await wait(25); boundary.probesBeforeRecovery = controlled.state.probes; }
    }
  } finally {
    await controlled.finish();
    await within(pending,2500);
    if (probe) await within(probe,1000);
  }
  return { fixture, controlled, result, boundary };
}
