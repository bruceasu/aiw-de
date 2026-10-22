'use strict';
const result = document.getElementById('configuration-result');
const owner = document.querySelector('meta[name="configuration-owner"]').content;
async function action(path, value) {
  const response = await fetch('/api/r1/authority/execution-configurations/' + path, {method:'POST', headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]').content}, body:JSON.stringify(value)});
  const data = await response.json();
  result.textContent = JSON.stringify(data, null, 2);
  if (!response.ok) throw new Error(data.code || '操作未提交');
  return data;
}
async function refresh() { document.getElementById('configuration-state').textContent = JSON.stringify(await action('state', {}), null, 2); }
function form(id, submit) {
  document.getElementById(id).addEventListener('submit', async event => {
    event.preventDefault();
    try { await submit(new FormData(event.target)); } catch (error) { result.textContent = error.message; }
  });
}
form('prepare-configuration', async data => {
  const intent = Object.fromEntries(data);
  for (const key of ['intentVersion','expectedGeneration','policyRevision']) intent[key] = Number(intent[key]);
  intent.dependencies = intent.dependencies.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
  intent.configRef = null; intent.candidate = null;
  await action('prepare', intent);
});
form('prepare-configuration-json', async data => { await action('prepare', JSON.parse(data.get('intent'))); });
form('save-configuration', async data => { await action('candidates', JSON.parse(data.get('candidate'))); });
form('propose-configuration', async data => { await action('propose', JSON.parse(data.get('assembly'))); });
form('register-configuration-asset', async data => {
  const asset = Object.fromEntries(data); asset.ownerId = owner;
  asset.digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(asset.content))), value => value.toString(16).padStart(2,'0')).join('');
  await action('assets', asset);
});
document.getElementById('refresh-configurations').addEventListener('click', () => refresh().catch(error => { result.textContent = error.message; }));
if (owner) refresh().catch(error => { result.textContent = error.message; });
