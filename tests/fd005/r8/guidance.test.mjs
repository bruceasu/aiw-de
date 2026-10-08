import { assert, behavior, outcome } from './harness.mjs';
import { apiFixture, memoryFetch, executeFixture, sseResponse, textItem, assertCompleted, assertNoComplete } from './api-fixture.mjs';

const approved = [
  { id: 'approved-a', profile: { provider: 'openai', runtime: 'openai-agents', model: 'synthetic-model-a', effort: 'none', fast: false, level: 1 } },
  { id: 'approved-b', profile: { provider: 'openai', runtime: 'openai-agents', model: 'synthetic-model-b', effort: 'low', fast: false, level: 2 } },
];
const guidance = (previous = null, allowedProfiles = approved) => ({ version: 'synthetic-approved-v1', allowedProfiles: structuredClone(allowedProfiles), previous, suggestedProfile: 'approved-b' });
const selected = async (id, input, expected) => {
  const fixture = await apiFixture(id, 'coder', { task: { aiGuidance: input } });
  const value = fixture.adapter.select(fixture.task);
  assert.equal(value.id, expected); assert.deepEqual(value.profile, approved.find(item => item.id === expected).profile);
  assert.ok(typeof value.reason === 'string' && value.reason.length > 0);
};
behavior('API-model-selection', async () => {
  const fixture = await apiFixture('approved-model-selection', 'coder', { task: { aiGuidance: guidance() } });
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), request => { assert.equal(request.model, 'synthetic-model-a'); return sseResponse(request, [textItem(fixture.example.finalResponse)]); });
  await assertCompleted(fixture, run.value); assert.equal(run.requests.length, 1);
  const start = fixture.client.calls.find(item => item.method === 'start');
  assert.equal(start.args[1].id, 'approved-a'); assert.deepEqual(start.args[1].profile, approved[0].profile);
});
behavior('MODELSELECT-escalate-model', () => selected('guidance-model', guidance({ profileId: 'approved-a', failureCategory: 'MODEL_ERROR', state: 'FAILED' }), 'approved-b'));
behavior('MODELSELECT-escalate-output', () => selected('guidance-output', guidance({ profileId: 'approved-a', failureCategory: 'OUTPUT_INVALID', state: 'FAILED' }), 'approved-b'));
behavior('MODELSELECT-hold-tool', () => selected('guidance-tool', guidance({ profileId: 'approved-a', failureCategory: 'TOOL_ERROR', state: 'FAILED' }), 'approved-a'));
behavior('MODELSELECT-cap-end', () => selected('guidance-cap', guidance({ profileId: 'approved-b', failureCategory: 'MODEL_ERROR', state: 'FAILED' }), 'approved-b'));
behavior('MODELSELECT-filter', () => selected('guidance-filter', guidance(null, [{ id: 'not-compatible', profile: { ...approved[0].profile, provider: 'unsupported' } }, ...approved]), 'approved-a'));
for (const [id, changes] of [
  ['MODELSELECT-deny-fast', { fast: true }], ['MODELSELECT-deny-provider', { provider: 'unsupported' }],
  ['MODELSELECT-deny-runtime', { runtime: 'unsupported' }], ['MODELSELECT-deny-effort', { effort: 'unsupported' }],
  ['MODELSELECT-deny-empty', null],
]) behavior(id, async () => {
  const fixture = await apiFixture(id, 'coder', { task: { aiGuidance: guidance(null, changes ? [{ id: 'incompatible', profile: { ...approved[0].profile, ...changes } }] : []) } });
  assert.throws(() => fixture.adapter.select(fixture.task));
  const run = await memoryFetch(() => outcome(executeFixture(fixture)), () => { throw new Error('An unapproved model must not run'); });
  assert.equal(run.requests.length, 0); assertNoComplete(fixture);
});
