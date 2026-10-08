import { assert, fs, path, behavior, outcome, deferred, within, directoryText } from './harness.mjs';
import { roles } from './cases.mjs';
import { evidenceRows } from './diagnostics.mjs';
import { runtime, createApiAttemptAdapter, profileApi, instructionApi, apiFixture, memoryClient, memoryFetch, executeFixture,
  textItem, sseResponse, httpModelError, cancelledFetch, assertCompleted, assertNoComplete, callCount, completeCall, rolesByName } from './api-fixture.mjs';

for (const [role] of roles) {
  behavior(`API-${role}-success`, async () => {
    const fixture = await apiFixture(`api-${role}-success`, role);
    const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem(fixture.example.finalResponse)]));
    await assertCompleted(fixture, run.value);
    assert.equal(run.requests.length, 1);
    assert.equal(fixture.profile.readOnly, fixture.example.readOnly);
  });
  behavior(`API-${role}-failure`, async () => {
    const fixture = await apiFixture(`api-${role}-failure`, role);
    const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => httpModelError(400));
    assertNoComplete(fixture); assert.equal(callCount(fixture, 'fail'), 1); assert.equal(run.requests.length, 1);
    assert.equal(fixture.client.calls.find(item => item.method === 'fail').args[3], 'MODEL_ERROR');
  });
  behavior(`API-${role}-capability-reject`, async () => {
    const fixture = await apiFixture(`api-${role}-deny`, role, { task: { taskType: role === 'coder' ? 'CODE_REVIEW' : 'IMPLEMENTATION' } });
    const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => { throw new Error('Model must not start for mismatched task'); });
    assertNoComplete(fixture); assert.equal(run.requests.length, 0);
    assert.throws(() => fixture.adapter.profile(fixture.task), error => error.code === 'ROLE_TASK_TYPE_DENIED' && error.category === 'CONFIGURATION');
  });
  behavior(`API-${role}-report`, async () => {
    const fixture = await apiFixture(`api-${role}-report`, role);
    const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem(fixture.example.finalResponse)]));
    await assertCompleted(fixture, run.value);
    const report = completeCall(fixture).args[3];
    if (fixture.example.report === null) assert.equal(report, null);
    else {
      assert.equal(report.verdict, fixture.example.expectedVerdict || fixture.example.report.verdict);
      assert.ok(typeof report.summary === 'string' && report.summary.trim().length > 0);
      if (fixture.example.report) assert.equal(report.summary, fixture.example.report.summary);
    }
  });
  behavior(`API-${role}-cancel`, async () => {
    const controller = new AbortController(), started = deferred();
    const fixture = await apiFixture(`api-${role}-cancel`, role, { config: { forceSignal: controller.signal } });
    const run = await memoryFetch(async () => {
      const pending = outcome(executeFixture(fixture));
      const request = await within(started.promise); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
      const result = await within(pending, 2500); assert.equal(request.signal.aborted, true); return result;
    }, cancelledFetch(started));
    assertNoComplete(fixture); assert.equal(run.requests.length, 1);
  });
}

behavior('ROLE-preserved-reject', async () => {
  for (const role of ['release-readiness', 'orchestrator']) {
    assert.throws(() => profileApi.assertExecutableRole(role));
    assert.throws(() => createApiAttemptAdapter({ role, skillsRoot: 'synthetic-never-read' },
      { execute: async () => { throw new Error('SYNTHETIC_RESERVED_ROLE_MUST_NOT_START'); }, settled: async () => true,
        assertSuccessful: () => {}, clear: () => {} }, { fixture: 'synthetic-factory-role-admission' }));
  }
});
behavior('ROLE-verifier-alias', async () => {
  const profile = profileApi.getProfile('verifier', 'INTEGRATION_VERIFICATION');
  const expected = profileApi.getProfile('integration-verifier', 'INTEGRATION_VERIFICATION');
  assert.deepEqual(profile, expected); assert.equal(profile.readOnly, true);
});
behavior('REPORT-semantic-empty', async () => {
  for (const role of ['requirements', 'architect', 'planner']) assert.throws(() => instructionApi.completionReport(rolesByName.get(role).taskType, ' \n '));
});
behavior('REPORT-verdict-missing', async () => {
  for (const role of ['reviewer', 'verifier', 'integration-verifier']) assert.throws(() => instructionApi.completionReport(rolesByName.get(role).taskType, 'Synthetic report lacks verdict.'));
});
behavior('REPORT-verdict-final', async () => {
  for (const role of ['reviewer', 'verifier', 'integration-verifier']) for (const text of ['AIW_VERDICT: PASS\nMore text.', 'AIW_VERDICT: UNKNOWN']) assert.throws(() => instructionApi.completionReport(rolesByName.get(role).taskType, text));
});
behavior('REPORT-readonly-dirty', async () => {
  const fixture = await apiFixture('readonly-dirty', 'reviewer');
  const file = path.join(fixture.workspace, 'original.txt'); await fs.writeFile(file, 'unchanged');
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), async request => {
    await fs.writeFile(file, 'synthetic external change during model');
    return sseResponse(request, [textItem(fixture.example.finalResponse)]);
  });
  assertNoComplete(fixture); assert.equal(callCount(fixture, 'fail'), 1); assert.equal(run.requests.length, 1);
});
behavior('REPORT-manifest', async () => {
  // Actual role reports retain the public Protocol v1 shapes; this does not validate a deployment manifest.
  const fixture = await apiFixture('manifest-report', 'planner');
  const report = fixture.adapter.report(fixture.task.taskType, fixture.example.finalResponse);
  assert.deepEqual(Object.keys(report).sort(), ['summary', 'verdict']);
  assert.deepEqual(report, fixture.example.report);
  for (const role of ['coder', 'integrator', 'repairer']) assert.equal(instructionApi.completionReport(rolesByName.get(role).taskType, 'Synthetic code completion.'), null);
});
behavior('LEASE-lost', async () => {
  const started = deferred();
  let heartbeatCount = 0, fetchObserved = false;
  const fixture = await apiFixture('lease-lost', 'coder', { client: { heartbeat: async () => { if (!fetchObserved) return; heartbeatCount++; throw new runtime.ProtocolError(409); } } });
  await memoryFetch(async () => {
    const pending = outcome(executeFixture(fixture)); await within(started.promise);
    await within(pending, 2500);
  }, (request, signal) => { fetchObserved = true; return cancelledFetch(started)(request, signal); });
  assertNoComplete(fixture); assert.ok(heartbeatCount >= 1);
});
behavior('COMPLETE-response-lost', async () => {
  const fixture = await apiFixture('complete-lost', 'coder', { client: { complete: async () => { throw new Error('SYNTHETIC_COMPLETE_RESPONSE_LOST'); } } });
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => sseResponse(request, [textItem(fixture.example.finalResponse)]));
  assert.equal(run.requests.length, 1); assert.equal(callCount(fixture, 'complete'), 1);
  const rows = (await evidenceRows(fixture.config.evidenceRoot)).map(row => row.metadata);
  assert.ok(rows.some(row => row.event === 'attempt-failed' && row.status === 'report-unknown' && row.reportStatus === 'unknown'),
    'Check the corresponding CP terminal receipt, not arbitrary unknown resource/usage text');
});
behavior('API-profile-legacy', async () => {
  const fixture = await apiFixture('legacy-profile', 'reviewer');
  const compat = JSON.parse(await fs.readFile(new URL('../../../workers/runtime-contract.examples.json', import.meta.url), 'utf8')).compatibility.find(item => item.profile);
  const file = path.join(fixture.directory, 'legacy.toml');
  const toml = Object.entries(compat.profile).map(([key, value]) => `${key} = ${JSON.stringify(value)}`).join('\n');
  await fs.writeFile(file, toml);
  const profile = profileApi.loadProfile(file);
  assert.equal(profile.readOnly, true); assert.deepEqual(profile.allowedTaskTypes, ['CODE_REVIEW']);
});
behavior('API-skill-complete', async () => {
  const fixture = await apiFixture('skill-complete', 'coder');
  const context = new runtime.ContextAssembler({ maxInputBytes: 200000 });
  const prompt = fixture.adapter.prompt(fixture.task, context);
  for (const name of [...fixture.profile.protocolSkills, fixture.profile.primarySkill]) for (const marker of ['SYNTHETIC_FULL_SKILL_MAIN_', 'SYNTHETIC_REFERENCE_', 'SYNTHETIC_AGENT_']) assert.ok(prompt.includes(marker + name));
  assert.ok(context.sources.size >= 3);
});
behavior('API-start-failure', async () => {
  const fixture = await apiFixture('start-failure', 'coder', { client: { start: async () => { throw new runtime.ProtocolError(409); } } });
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => { throw new Error('No model request expected'); });
  assertNoComplete(fixture); assert.equal(run.requests.length, 0); assert.equal(callCount(fixture, 'start'), 1);
});
behavior('API-heartbeat-failure', async () => {
  const started = deferred(); let fetchObserved = false;
  const fixture = await apiFixture('heartbeat-failure', 'coder', { client: { heartbeat: async () => { if (fetchObserved) throw new runtime.ProtocolError(409); } } });
  await memoryFetch(async () => {
    const pending = outcome(executeFixture(fixture)); const { signal } = await within(started.promise);
    await within(pending, 2500); assert.equal(signal.aborted, true);
  }, (request, signal) => { fetchObserved = true; return cancelledFetch(started)(request, signal); });
  assertNoComplete(fixture);
});
behavior('API-evidence-failure', async () => {
  const fixture = await apiFixture('evidence-failure', 'coder');
  const collision = path.join(fixture.directory, 'evidence-collision'); await fs.writeFile(collision, 'owned synthetic file prevents directory creation');
  fixture.config.evidenceRoot = collision;
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => { throw new Error('No model request expected'); });
  assertNoComplete(fixture); assert.equal(run.requests.length, 0);
});
behavior('API-operation-wait', async () => {
  const fixture = await apiFixture('operation-wait', 'coder');
  const started = deferred(), release = deferred(), settledStarted = deferred();
  const controller = new AbortController(); fixture.config.forceSignal = controller.signal;
  let ended = false;
  const fakeRuntime = {
    execute: async () => { started.resolve(); await release.promise; ended = true; return 'Synthetic finished.'; },
    settled: async () => { settledStarted.resolve(); await release.promise; assert.equal(ended, true); return true; },
    assertSuccessful: () => {}, clear: () => { assert.equal(ended, true, 'Clear must not precede resource termination'); },
  };
  const adapter = createApiAttemptAdapter({ role: 'coder', skillsRoot: fixture.skillsRoot }, fakeRuntime, { fixture: 'synthetic-runtime-only' });
  const pending = outcome(runtime.runClaimedAttempt(fixture.config, fixture.client, fixture.task, adapter));
  await within(started.promise); controller.abort(new runtime.RuntimeFault('EXPLICIT_TERMINATION'));
  try { assert.equal(ended, false); }
  finally { release.resolve(); await within(pending, 2500); }
  assertNoComplete(fixture); assert.equal(ended, true); await within(settledStarted.promise);
});
