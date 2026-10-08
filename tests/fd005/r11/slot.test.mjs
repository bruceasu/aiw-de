import { assert, fs, path, behavior, deferred, within, outcome, wait } from './harness.mjs';
import { appendDiagnostic, verifyAll } from './diagnostics.mjs';
import { runtime, apiFixture, callCount, memoryClient } from './api-fixture.mjs';
import { controlledAdapter } from './attempt-fixture.mjs';

async function unknownSlot(id, { observerThrows = false, uncertainThrows = false, uncertainHangs = false } = {}) {
  const fixture = await apiFixture(id,'coder',{ config: { pollMs: 5, taskTimeoutMs: 6000, cleanupTimeoutMs: 80 } });
  const controlled = controlledAdapter(fixture,{ blockSettled: true }), callbackRelease = deferred();
  let claims = 0, creates = 0, outcomes = 0, notifications = 0, forcedFixtureStop = false, observedOutcome;
  fixture.client.claim = async () => {
    claims++; appendDiagnostic('slot-calls.jsonl',{ fixtureId: fixture.task.attemptId, phase: 'claim', claims });
    if (claims === 1) return fixture.task;
    forcedFixtureStop = true; return null; // Bound a faulty loop; second claim still makes the test fail.
  };
  const pending = outcome(runtime.runWorkerSlot({
    cfg: fixture.config, client: fixture.client, shouldStop: () => forcedFixtureStop,
    createAdapter: () => { creates++; return controlled.adapter; },
    onOutcome: value => { outcomes++; observedOutcome = value; appendDiagnostic('slot-calls.jsonl',{ phase: 'outcome', status: value.status, resourceUncertain: value.resourceUncertain }); if (observerThrows) throw new Error('SYNTHETIC_OUTCOME_OBSERVER_ERROR'); },
    onUncertain: async () => { notifications++; appendDiagnostic('slot-calls.jsonl',{ phase: 'host-uncertain-notified', notifications }); if (uncertainThrows) throw new Error('SYNTHETIC_UNCERTAIN_OBSERVER_ERROR'); if (uncertainHangs) await callbackRelease.promise; },
    onError: error => appendDiagnostic('slot-calls.jsonl',{ phase: 'observer-error', code: runtime.errorCode(error) }),
  }));
  let returned;
  try { await within(controlled.settledStarted.promise); returned = await within(pending,1200); }
  finally { callbackRelease.resolve(); await controlled.finish(); await within(pending,2000); }
  await verifyAll({
    oneClaimOnly: () => assert.equal(claims,1),
    oneAdapterOnly: () => assert.equal(creates,1),
    unknownOutcomeObserved: () => assert.equal(observedOutcome?.resourceUncertain,true),
    stoppedWithoutSyntheticHostFlag: () => assert.equal(forcedFixtureStop,false),
    noComplete: () => assert.equal(callCount(fixture,'complete'),0),
    ...(observerThrows ? {} : { hostNotified: () => assert.equal(notifications,1) }),
  });
  return { fixture, controlled, returned, claims, creates, outcomes, notifications };
}
behavior('SLOT-resource-unknown-stop', () => unknownSlot('slot-resource-unknown-stop'));
behavior('SLOT-outcome-observer-throws', () => unknownSlot('slot-outcome-observer-throws',{ observerThrows: true }));
behavior('SLOT-uncertain-observer-throws', () => unknownSlot('slot-uncertain-observer-throws',{ uncertainThrows: true }));
behavior('SLOT-uncertain-observer-timeout', () => unknownSlot('slot-uncertain-observer-timeout',{ uncertainHangs: true }));

behavior('SLOT-drain-inflight', async () => {
  const fixture = await apiFixture('slot-drain-inflight','coder',{ config: { pollMs: 5, taskTimeoutMs: 6000, cleanupTimeoutMs: 300 } });
  const controlled = controlledAdapter(fixture,{ blockExecution: true });
  let stop = false, claims = 0, finished = false, observedOutcome;
  fixture.client.claim = async () => { claims++; return fixture.task; };
  const pending = outcome(runtime.runWorkerSlot({ cfg: fixture.config, client: fixture.client,
    shouldStop: () => stop, createAdapter: () => controlled.adapter,
    onOutcome: value => { observedOutcome = value; }, onUncertain: () => { throw new Error('Expected settled drain, not uncertain'); },
  })).then(value => { finished = true; return value; });
  try {
    const input = await within(controlled.started.promise); stop = true;
    await wait(25); assert.equal(finished,false); assert.equal(input.attempt.signal.aborted,false);
  } finally { await controlled.finish(); await within(pending,2000); }
  await verifyAll({
    oneClaim: () => assert.equal(claims,1),
    completedInflight: () => assert.equal(observedOutcome?.status,'completed'),
    resourceKnown: () => assert.equal(observedOutcome?.resourceUncertain,false),
    oneComplete: () => assert.equal(callCount(fixture,'complete'),1),
  });
});
behavior('SLOT-force-before-start', async () => {
  const controller = new AbortController(), claimed = deferred(), claimRelease = deferred();
  const fixture = await apiFixture('slot-force-before-start','coder',{ config: { pollMs: 5, cleanupTimeoutMs: 80, forceSignal: controller.signal } });
  let claims = 0, creates = 0;
  fixture.client.claim = async () => { claims++; claimed.resolve(); return claimRelease.promise; };
  const pending = outcome(runtime.runWorkerSlot({ cfg: fixture.config, client: fixture.client, shouldStop: () => false,
    createAdapter: () => { creates++; return fixture.adapter; } }));
  try { await within(claimed.promise); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION')); claimRelease.resolve(fixture.task); await within(pending,1200); }
  finally { claimRelease.resolve(fixture.task); }
  await verifyAll({
    oneClaim: () => assert.equal(claims,1),
    noAdapterAfterForce: () => assert.equal(creates,0),
    noStart: () => assert.equal(callCount(fixture,'start'),0),
    noComplete: () => assert.equal(callCount(fixture,'complete'),0),
  });
});
behavior('SLOT-shared-host-stop', async () => {
  const controller = new AbortController(), otherClaimed = deferred(), otherRelease = deferred();
  const primary = await apiFixture('slot-shared-primary','coder',{ config: { pollMs: 5, cleanupTimeoutMs: 80, forceSignal: controller.signal } });
  const other = await apiFixture('slot-shared-other','coder',{ config: { pollMs: 5, cleanupTimeoutMs: 80, forceSignal: controller.signal } });
  const controlled = controlledAdapter(primary,{ blockSettled: true });
  let stop = false, primaryClaims = 0, otherClaims = 0, otherCreates = 0, drainNotifications = 0;
  primary.client.claim = async () => { primaryClaims++; if (primaryClaims === 1) return primary.task; stop = true; return null; };
  other.client.claim = async () => { otherClaims++; otherClaimed.resolve(); return otherRelease.promise; };
  primary.client.drain = async () => { drainNotifications++; appendDiagnostic('slot-calls.jsonl',{ phase: 'synthetic-host-drain', drainNotifications }); };
  const otherPending = outcome(runtime.runWorkerSlot({ cfg: other.config, client: other.client, shouldStop: () => stop,
    createAdapter: () => { otherCreates++; return other.adapter; } }));
  await within(otherClaimed.promise);
  const primaryPending = outcome(runtime.runWorkerSlot({ cfg: primary.config, client: primary.client, shouldStop: () => stop,
    createAdapter: () => controlled.adapter,
    onUncertain: async () => { stop = true; controller.abort(new runtime.RuntimeFault('RESOURCE_TERMINATION_UNCERTAIN')); await primary.client.drain(); },
  }));
  try { await within(primaryPending,1500); otherRelease.resolve(other.task); await within(otherPending,1200); }
  finally { stop = true; otherRelease.resolve(other.task); await controlled.finish(); await within(Promise.all([primaryPending,otherPending]),2000); }
  await verifyAll({
    primaryRetired: () => assert.equal(primaryClaims,1),
    hostNotifiedDrain: () => assert.equal(drainNotifications,1),
    otherReceivedAtMostOneClaim: () => assert.equal(otherClaims,1),
    otherNeverCreatedAdapter: () => assert.equal(otherCreates,0),
    otherNeverStarted: () => assert.equal(callCount(other,'start'),0),
    noPrimaryComplete: () => assert.equal(callCount(primary,'complete'),0),
  });
});
