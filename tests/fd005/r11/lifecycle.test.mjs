import { assert, fs, path, behavior, outcome } from './harness.mjs';
import { appendDiagnostic, evidenceRows, verifyAll } from './diagnostics.mjs';
import { boundedAttempt } from './attempt-fixture.mjs';
import { apiFixture, memoryFetch, executeFixture, sseResponse, textItem, assertCompleted, assertNoComplete, callCount } from './api-fixture.mjs';

const metadata = rows => rows.map(row => row.metadata);
async function checkBounded(id, activeIO, options = {}) {
  const result = await boundedAttempt(id,{ activeIO, ...options });
  const { fixture, controlled, boundary, result: run } = result;
  await verifyAll({
    returnedNormally: () => assert.equal(run.rejected,false),
    explicitResourceUnknown: () => assert.equal(run.value?.resourceUncertain,true),
    noSuccessfulPost: () => assertNoComplete(fixture),
    notCompletedStatus: () => assert.notEqual(run.value?.status,'completed'),
    worktreeRetained: () => assert.equal(boundary.worktreeExists,true),
    runtimeNotClearedBeforeEnd: () => assert.equal(boundary.clearBeforeEnd,false),
    runtimeNotClearedAtUnknownBoundary: () => assert.equal(boundary.clearCalls,0),
    unendedResourceActuallyObserved: () => assert.equal(boundary.resourceEnded,false),
    ...(activeIO && options.uncertainTermination ? {
      quarantinedProbeRejected: () => { assert.equal(boundary.probeResult.rejected,false); assert.equal(boundary.probeResult.value.rejected,true); },
      noFutureOperationAfterRecovery: () => assert.equal(controlled.state.probes,0),
    } : activeIO ? {
      pendingLeaseHeld: () => assert.equal(boundary.probesBeforeRecovery,0),
      ordinaryEndedOperationReleasesLease: () => assert.equal(controlled.state.probes,1),
    } : {}),
  });
  return result;
}
behavior('API-adapter-settled-bound', () => checkBounded('adapter-settled-bound',false));
behavior('API-active-io-bound', () => checkBounded('active-io-bound',true));
behavior('API-uncertain-termination-bound', () => checkBounded('uncertain-termination-bound',true,{ uncertainTermination: true }));
behavior('LOG-resource-unknown-consistency', async () => {
  const { fixture, boundary } = await checkBounded('resource-unknown-consistency',false,{ settledReturnsFalse: true, cleanupMs: 400 });
  const rows = metadata(boundary.rows);
  await verifyAll({
    explicitUnknownResourceEvent: () => assert.ok(rows.some(row => row.event === 'resource-terminal' && row.status === 'unknown' && row.code === 'PROCESS_TREE_UNCERTAIN')),
    noAcceptedReport: () => assert.ok(!rows.some(row => row.event === 'report-posted' && row.reportStatus === 'accepted')),
    noReadyMarker: () => assert.ok(!boundary.rows.some(row => row.file.endsWith('terminal.json') && row.metadata.status === 'ready-to-report')),
    noCompletedTerminal: () => assert.ok(!rows.some(row => row.event === 'attempt-terminal' && row.status === 'completed')),
    noLateCompleteAfterRecovery: () => assertNoComplete(fixture),
  });
});
behavior('LOG-success-consistency', async () => {
  let beforeComplete = [];
  const fixture = await apiFixture('success-consistency','coder',{ client: { complete: async () => {
    beforeComplete = await evidenceRows(fixture.config.evidenceRoot);
    appendDiagnostic('terminal-phases.jsonl',{ phase: 'before-complete', records: beforeComplete });
  } } });
  const run = await memoryFetch(() => outcome(executeFixture(fixture)),request => sseResponse(request,[textItem(fixture.example.finalResponse)]));
  const rows = metadata(await evidenceRows(fixture.config.evidenceRoot)), before = metadata(beforeComplete);
  await verifyAll({
    completed: () => assertCompleted(fixture,run.value),
    explicitKnownResourceReturn: () => assert.equal(run.value.value?.resourceUncertain,false),
    resourceSavedBeforeComplete: () => assert.ok(before.some(row => row.event === 'resource-terminal' && row.status === 'settled')),
    readyFileSavedBeforeComplete: () => assert.ok(beforeComplete.some(row => row.file.endsWith('terminal.json') && row.metadata.status === 'ready-to-report')),
    readyEventSavedBeforeComplete: () => assert.ok(before.some(row => row.event === 'attempt-terminal' && row.status === 'ready-to-report')),
    acceptedPost: () => assert.ok(rows.some(row => row.event === 'report-posted' && row.reportStatus === 'accepted')),
    noFalseResourceUnknown: () => assert.ok(!rows.some(row => row.event === 'resource-terminal' && (row.status === 'unknown' || row.code === 'PROCESS_TREE_UNCERTAIN'))),
    endedRequestNotArtificiallyAborted: () => assert.ok(run.requests.every(request => !request.signal?.aborted)),
  });
});
behavior('LOG-complete-unknown-consistency', async () => {
  const fixture = await apiFixture('complete-unknown-consistency','coder',{ client: { complete: async () => { throw new Error('SYNTHETIC_COMPLETE_REPLY_LOST'); } } });
  const run = await memoryFetch(() => outcome(executeFixture(fixture)),request => sseResponse(request,[textItem(fixture.example.finalResponse)]));
  const rows = metadata(await evidenceRows(fixture.config.evidenceRoot));
  await verifyAll({
    publicUnknownReport: () => { assert.equal(run.value.rejected,false); assert.equal(run.value.value?.status,'report-unknown'); },
    independentKnownResource: () => assert.equal(run.value.value?.resourceUncertain,false),
    oneCompleteNoRetry: () => assert.equal(callCount(fixture,'complete'),1),
    noFailAfterComplete: () => assert.equal(callCount(fixture,'fail'),0),
    reportUnknownEvent: () => assert.ok(rows.some(row => row.event === 'attempt-failed' && row.status === 'report-unknown' && row.reportStatus === 'unknown')),
    resourceRemainsSettled: () => { assert.ok(rows.some(row => row.event === 'resource-terminal' && row.status === 'settled')); assert.ok(!rows.some(row => row.event === 'resource-terminal' && row.status === 'unknown')); },
    noAcceptedResponseClaim: () => assert.ok(!rows.some(row => row.event === 'report-posted' && row.reportStatus === 'accepted')),
  });
});
