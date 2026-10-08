import assert from 'node:assert/strict';
import { spawn, execFile } from 'node:child_process';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { promisify } from 'node:util';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

const execFileAsync = promisify(execFile);
const projectRoot = path.resolve(import.meta.dirname, '../..');
const maven = 'D:/green/apache-maven-3.9.9/bin/mvn.cmd';
const jdk = 'D:/green/jdk-21.0.8';

async function unusedLoopbackPort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => server.once('error', reject).listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}

async function post(base, route, body, token) {
  const response = await fetch(base + route, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(5000),
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); }
    catch { data = text.slice(0, 1000); }
  }
  return { status: response.status, data };
}

async function waitForServer(base, child, output) {
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error('Isolated Control Plane exited before becoming ready: ' + output());
    try {
      const response = await fetch(base + '/ui', { signal: AbortSignal.timeout(1000) });
      if (response.ok) return;
    } catch { /* Startup is still in progress. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error('Isolated Control Plane did not become ready: ' + output());
}

async function isPortOpen(port) {
  return await new Promise(resolve => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
    socket.setTimeout(1000, () => { socket.destroy(); resolve(false); });
  });
}

test('two legacy refs resolving to one physical directory cannot both claim DIRECT tasks', { timeout: 120000 }, async () => {
  const ownedRoot = await mkdtemp(path.join(os.tmpdir(), 'fd006-alias-claim-'));
  const samePath = path.join(ownedRoot, 'shared-directory');
  const otherPath = path.join(ownedRoot, 'other-directory');
  await mkdir(samePath);
  await mkdir(otherPath);
  const config = `aidev:
  workspaces:
    local:
      repositories:
        fd006first: ${JSON.stringify(samePath.replaceAll('\\', '/'))}
        fd006alias: ${JSON.stringify(samePath.replaceAll('\\', '/'))}
        fd006other: ${JSON.stringify(otherPath.replaceAll('\\', '/'))}
`;
  const configPath = path.join(ownedRoot, 'application.yaml');
  await writeFile(configPath, config);
  const port = await unusedLoopbackPort();
  const base = `http://127.0.0.1:${port}`;
  const token = randomUUID();
  const environment = {
    ...process.env,
    JAVA_HOME: jdk,
    PATH: `${jdk}/bin;${process.env.PATH ?? ''}`,
    SPRING_PROFILES_ACTIVE: 'sqlite',
    SPRING_CONFIG_ADDITIONAL_LOCATION: pathToFileURL(configPath).href,
    AIW_DB_URL: `jdbc:sqlite:${path.join(ownedRoot, 'control.db').replaceAll('\\', '/')}`,
    AIW_BIND_ADDRESS: '127.0.0.1',
    AIW_PORT: String(port),
    AIW_LEASE_RECOVERY_ENABLED: 'false',
    AIW_WEB_CONSOLE_AUTH_ENABLED: 'false',
    AIW_WORKER_TOKEN: token,
  };
  let output = '';
  const child = spawn(maven, ['-o', '-q', '-DskipTests', 'spring-boot:run'], {
    cwd: projectRoot,
    env: environment,
    shell: true,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', chunk => { output = (output + chunk.toString()).slice(-6000); });
  child.stderr.on('data', chunk => { output = (output + chunk.toString()).slice(-6000); });

  try {
    await waitForServer(base, child, () => output);
    const workerId = 'fd006-alias-worker';
    const registered = await post(base, '/api/worker-protocol/v1/workers/register', {
      workerId,
      runtimeType: 'test-only',
      capabilities: ['requirement-analysis', 'repo:fd006first', 'repo:fd006alias', 'repo:fd006other'],
      maxConcurrency: 3,
    }, token);
    assert.ok(registered.status >= 200 && registered.status < 300, JSON.stringify(registered));

    async function createRun(requestId, repositoryRef) {
      const response = await post(base, '/api/r8/requirements', {
        requestId,
        requirement: 'Isolated FD-006 directory admission check.',
        repositoryRef,
        technicalCapabilities: [],
        priority: 'HIGH',
        maxParallelism: 1,
      }, token);
      assert.ok(response.status >= 200 && response.status < 300, JSON.stringify(response));
    }

    await createRun('fd006-first', 'fd006first');
    const first = await post(base, '/api/worker-protocol/v1/tasks/claim', { workerId }, token);
    assert.equal(first.status, 200, JSON.stringify(first));
    assert.equal(first.data.workspace.repositoryRef, 'fd006first');
    assert.equal(first.data.workspace.mode, 'DIRECT');

    await createRun('fd006-alias', 'fd006alias');
    const blockedAlias = await post(base, '/api/worker-protocol/v1/tasks/claim', { workerId }, token);
    assert.equal(blockedAlias.status, 204, 'Same physical directory was claimed twice: ' + JSON.stringify(blockedAlias));

    await createRun('fd006-other', 'fd006other');
    const differentDirectory = await post(base, '/api/worker-protocol/v1/tasks/claim', { workerId }, token);
    assert.equal(differentDirectory.status, 200, JSON.stringify(differentDirectory));
    assert.equal(differentDirectory.data.workspace.repositoryRef, 'fd006other');
  } finally {
    if (child.pid && child.exitCode === null && child.signalCode === null) {
      try {
        await execFileAsync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, timeout: 10000 });
      } catch { /* The test-owned process may have exited already. */ }
    }
    if (child.exitCode === null && child.signalCode === null) {
      let timeout;
      const stopped = await Promise.race([
        new Promise(resolve => child.once('exit', () => resolve(true))),
        new Promise(resolve => { timeout = setTimeout(() => resolve(false), 10000); }),
      ]);
      clearTimeout(timeout);
      if (!stopped) throw new Error(`Test-owned server did not stop; temporary directory retained: ${ownedRoot}`);
    }
    const deadline = Date.now() + 10000;
    while (await isPortOpen(port) && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    if (await isPortOpen(port)) throw new Error(`Server port remains open; temporary directory retained: ${ownedRoot}`);
    assert.equal(path.resolve(path.dirname(ownedRoot)), path.resolve(os.tmpdir()));
    assert.ok(path.basename(ownedRoot).startsWith('fd006-alias-claim-'));
    await rm(ownedRoot, { recursive: true, force: true });
  }
});
