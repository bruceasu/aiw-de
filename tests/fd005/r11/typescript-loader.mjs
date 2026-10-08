// Compiler reads actual production TypeScript; source/method bodies are never emitted to test evidence.
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';

let allowed, compiler;
export async function initialize({ repository }) {
  allowed = path.join(repository, 'workers', 'process-typescript-with-api', 'src');
  const require = createRequire(path.join(repository, 'workers', 'process-typescript-with-api', 'package.json'));
  compiler = require('typescript'); // Installed local compiler only. No resolver/download subprocess.
}
const isAllowed = filename => { const relative = path.relative(allowed, filename); return relative && !relative.startsWith('..') && !path.isAbsolute(relative); };
export async function resolve(specifier, context, nextResolve) {
  try { return await nextResolve(specifier, context); }
  catch (error) {
    if (!context.parentURL?.startsWith('file:') || !specifier.endsWith('.js') || !specifier.startsWith('.')) throw error;
    const url = new URL(specifier.slice(0, -3) + '.ts', context.parentURL);
    const filename = fileURLToPath(url);
    if (!isAllowed(filename) || !(await fs.stat(filename).catch(() => undefined))?.isFile()) throw error;
    return { url: pathToFileURL(filename).href, shortCircuit: true };
  }
}
export async function load(url, context, nextLoad) {
  if (!url.startsWith('file:') || !url.endsWith('.ts')) return nextLoad(url, context);
  const filename = fileURLToPath(url);
  if (!isAllowed(filename) || path.basename(filename) === 'index.ts') throw new Error('Only the declared production API modules may be loaded; Worker loop forbidden');
  const source = await fs.readFile(filename, 'utf8');
  await fs.appendFile(path.join(process.env.FD005_RESULT_ROOT, 'production-module-sha256.jsonl'), JSON.stringify({ syntheticTestOnly: true, productionModule: filename,
    sha256: createHash('sha256').update(source).digest('hex'), transform: 'installed-TypeScript-transpileModule-in-memory-no-source-map' }) + '\n');
  const output = compiler.transpileModule(source, { fileName: filename,
    compilerOptions: { target: compiler.ScriptTarget.ES2022, module: compiler.ModuleKind.ESNext, sourceMap: false, inlineSourceMap: false }, reportDiagnostics: false });
  return { format: 'module', source: output.outputText, shortCircuit: true };
}

