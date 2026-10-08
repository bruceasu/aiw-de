// Synthetic fixed CLI entry. It never calls a model, Control Plane, GitHub, or network.
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const directory = path.dirname(fileURLToPath(import.meta.url));
const state = JSON.parse(await fs.readFile(path.join(directory, 'plugin-state.json'), 'utf8'));
const leaked = Object.keys(process.env).some(key => key.startsWith('AIW_') || key.startsWith('OTEL_') || key === 'OPENAI_API_KEY' || key === 'DEBUG');
await fs.appendFile(path.join(directory, 'plugin-metadata.jsonl'), JSON.stringify({ syntheticTestOnly: true, args: process.argv.slice(2), cwd: process.cwd(), leakedEnvironment: leaked }) + '\n');
if (state.mode === 'timeout') setInterval(() => {}, 1000);
else if (state.mode === 'overflow') process.stdout.write('x'.repeat(4096));
else if (state.mode === 'malformed') process.stdout.write('{"ok":');
else if (state.mode === 'schema-invalid') process.stdout.write(JSON.stringify({ ok: true, action: 'list', issues: [], skills: [{ name: 12 }] }));
else {
  const action = process.argv[2];
  const output = { ok: true, action, issues: state.mode === 'partial' ? ['Synthetic discovery issue'] : [],
    skills: action === 'list' ? [{ name: 'synthetic-skill', description: 'Synthetic fixture only', source: 'synthetic-source' }] : [{ name: 'synthetic-skill', status: 'installed' }] };
  process.stdout.write(JSON.stringify(output));
}

