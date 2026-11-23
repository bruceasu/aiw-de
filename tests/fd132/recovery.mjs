import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdtempSync, readFileSync, realpathSync, rmSync} from 'node:fs';
import {createServer} from 'node:http';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';

const resources = 'src/main/resources/';
const taskPage = '/ui/plans/fixture-plan/tasks/step-two';
const evidence = {attemptId: 'attempt-two', evidenceRef: 'fixture/exit.json', evidenceDigest: 'a'.repeat(64), allProcessesQuiescent: true};
const expected = [evidence, {version: 7, action: 'retry'}];
const posts = [];
const taskHtml = readFileSync(resources + 'templates/ui/plan-task-detail.html', 'utf8').replace(
  /th:href="@\{\/ui\/authority\(stopTask=\$\{taskDetail\.task\.id\.value\}\)\} \+ '#authority-audit'"/,
  'href="/ui/authority?stopTask=step-two#authority-audit"');
const authorityHtml = readFileSync(resources + 'templates/ui/authority.html', 'utf8')
  .replace('th:content="${csrfToken}"', 'content="fixture-csrf"')
  .replace(/<span th:each="worker : \$\{workers\}" data-authority-identity[^>]*><\/span>/, '')
  .replace(/<option th:each="candidate : \$\{stopCandidates\}"[\s\S]*?<\/option>/,
    `<option value="other" data-task="other">Other attempt</option>
     <option value="attempt-two" data-task="step-two" data-plan="fixture-plan" data-workflow="true"
       data-evidence-ref="${evidence.evidenceRef}" data-evidence-digest="${evidence.evidenceDigest}">Interrupted attempt</option>`);

const server = createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const send = (type, content, code = 200) => { res.writeHead(code, {'Content-Type': type}); res.end(content); };
  const json = (body, code) => send('application/json', JSON.stringify(body), code);
  if (url.pathname === taskPage) return send('text/html; charset=utf-8', taskHtml);
  if (url.pathname === '/ui/authority') return send('text/html; charset=utf-8', authorityHtml);
  if (url.pathname === '/ui/authority.js') return send('text/javascript; charset=utf-8', readFileSync(resources + 'static/ui/authority.js'));
  if (url.pathname === '/ui/workflows') return send('text/html', '<p>Returned to the workflow</p>');
  if (url.pathname === '/api/r1/workflows/executions/fixture-plan' && posts.length === 1)
    return json({run: {state: 'FAILED', version: 7}, task: {id: {value: 'step-two'}, state: 'CANCELLED'}});
  const next = posts.length === 0 ? '/api/r1/authority/execution-tasks/step-two/stopped' : '/api/r1/workflows/executions/fixture-plan/control';
  if (req.method === 'POST' && url.pathname === next) {
    let body = '';
    for await (const chunk of req) body += chunk;
    posts.push({csrf: req.headers['x-csrf-token'], body: JSON.parse(body)});
    return json({recorded: true});
  }
  return json([], 404);
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const temp = realpathSync(tmpdir());
const profile = mkdtempSync(path.join(temp, 'fd132-chrome-'));
const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--remote-debugging-port=0',
  `--user-data-dir=${profile}`, '--no-first-run', '--disable-background-networking', '--disable-component-update',
  '--disable-sync', '--no-default-browser-check', 'about:blank'], {windowsHide: true, stdio: 'ignore'});
let ws;
try {
  async function until(read) {
    for (let i = 0; i < 80; i++) {
      try {
        const value = await read();
        if (value) return value;
      } catch (error) {
        if (!/Execution context was destroyed|Cannot find context/.test(error.message)) throw error;
      }
      await delay(100);
    }
    throw new Error('Timed out waiting for browser recovery state');
  }
  const port = await until(() => {try {return readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0];} catch {return null;}});
  const [tab] = await (await fetch(`http://127.0.0.1:${port}/json/list`, {signal: AbortSignal.timeout(3000)})).json();
  ws = new WebSocket(tab.webSocketDebuggerUrl);
  await once(ws, 'open');
  const calls = new Map();
  const pageErrors = [];
  let id = 0;
  ws.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.method === 'Runtime.exceptionThrown') {
      const failure = message.params.exceptionDetails;
      if (failure.url?.includes('/authority.js')) pageErrors.push(failure.exception?.description || failure.text);
    }
    const resolve = calls.get(message.id);
    if (resolve) {calls.delete(message.id); resolve(message);}
  });
  async function cdp(method, params = {}) {
    const request = {id: ++id, method, params};
    const message = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`CDP timeout ${method}`)), 3000);
      calls.set(request.id, message => {clearTimeout(timer); resolve(message);});
      ws.send(JSON.stringify(request));
    });
    assert.equal(message.error, undefined);
    return message.result;
  }
  async function evaluate(expression) {
    const response = await cdp('Runtime.evaluate', {expression, returnByValue: true});
    assert.equal(response.exceptionDetails, undefined, response.exceptionDetails?.exception?.description);
    return response.result.value;
  }
  await cdp('Runtime.enable');
  await cdp('Page.navigate', {url: origin + taskPage});
  await until(() => evaluate(`document.readyState === 'complete' && location.pathname === '${taskPage}'`));
  await evaluate(`document.querySelector('a[href*="stopTask"]').click()`);
  await until(() => {
    assert.deepEqual(pageErrors, [], 'Authority page script error');
    return evaluate(`document.querySelector('#execution-stop-panel')?.open && document.readyState === 'complete'`);
  });
  assert.deepEqual(await evaluate(`(() => {const {elements: e} = document.querySelector('#execution-stopped');
    return [e.attemptId.value, e.evidenceRef.value, e.evidenceDigest.value, e.evidenceRef.readOnly, e.allProcessesQuiescent.checked];})()`),
  ['attempt-two', evidence.evidenceRef, evidence.evidenceDigest, true, false]);
  await evaluate(`document.querySelector('#execution-stopped button[type=submit]').click()`);
  assert.equal(posts.length, 0, 'stop must require the quiescence confirmation');
  await evaluate(`document.querySelector('[name=allProcessesQuiescent]').click(); document.querySelector('#execution-stopped button[type=submit]').click()`);
  await until(() => evaluate(`!document.querySelector('#execution-stop-next').hidden`));
  await evaluate(`window.confirm = () => false; document.querySelector('#execution-stop-retry').click()`);
  assert.equal(posts.length, 1, 'cancel must not request a retry');
  await evaluate(`window.confirm = () => true; document.querySelector('#execution-stop-retry').click()`);
  await until(() => evaluate(`location.pathname === '/ui/workflows' && location.search === '?execution=fixture-plan'`));
  assert.deepEqual(posts, expected.map(body => ({csrf: 'fixture-csrf', body})));
  console.log('PASS Chrome recovery, matching attempt, quiescence, cancellation, versioned retry and return');
} finally {
  ws?.close();
  const stopped = once(chrome, 'exit');
  if (chrome.exitCode === null && chrome.kill()) await stopped;
  server.closeAllConnections();
  server.close();
  if (path.dirname(realpathSync(profile)) === temp) rmSync(profile, {recursive: true, force: true, maxRetries: 4, retryDelay: 200});
}
