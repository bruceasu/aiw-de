import assert from 'node:assert/strict';

const localModule = '../../extern-workers/process-typescript/runtime-core/index.mjs';
const oldModule = '../../workers/runtime-core/index.mjs';
let passed = 0;

async function check(name, operation) {
  await operation();
  passed += 1;
  process.stdout.write(`ok ${passed} - ${name}\n`);
}

const runtime = await import(localModule);

await check('package-local runtime entry imports and exposes shared attempt/slot APIs', async () => {
  assert.equal(typeof runtime.AttemptRunner, 'function');
  assert.equal(typeof runtime.runClaimedAttempt, 'function');
  assert.equal(typeof runtime.runWorkerSlot, 'function');
  assert.equal(typeof runtime.ControlPlaneClient, 'function');
});

await check('removed top-level runtime entry cannot be imported', async () => {
  await assert.rejects(import(oldModule), { code: 'ERR_MODULE_NOT_FOUND' });
});

await check('attempt without evidence refuses the operation', async () => {
  const attempt = new runtime.AttemptRunner({ timeoutMs: 1000 });
  let called = false;
  try {
    await assert.rejects(
      attempt.step('model', async () => { called = true; }),
      { code: 'ATTEMPT_EVIDENCE_REQUIRED' },
    );
    assert.equal(called, false);
  } finally {
    attempt.dispose();
  }
});

await check('UTF-8 output remains within its public byte budget', async () => {
  const output = runtime.boundedText('汉'.repeat(100), { maxBytes: 128 });
  assert.equal(output.complete, false);
  assert.equal(output.truncated, true);
  assert.ok(Buffer.byteLength(output.text, 'utf8') <= 128);
});

process.stdout.write(`1..${passed}\n`);
