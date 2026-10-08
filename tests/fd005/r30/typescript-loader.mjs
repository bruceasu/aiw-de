import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const root = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
const allowed = path.join(root, 'workers/process-typescript-with-api/src');
const compiler = createRequire(path.join(root, 'workers/process-typescript-with-api/package.json'))('typescript');
function inside(file) {
  const relative = path.relative(allowed, file);
  return relative && !relative.startsWith('..') && !path.isAbsolute(relative);
}
export async function resolve(specifier, context, nextResolve) {
  try { return await nextResolve(specifier, context); }
  catch (error) {
    if (!context.parentURL?.startsWith('file:') || !specifier.startsWith('.') || !specifier.endsWith('.js')) throw error;
    const file = fileURLToPath(new URL(specifier.slice(0, -3) + '.ts', context.parentURL));
    if (!inside(file) || !(await fs.stat(file).catch(() => undefined))?.isFile()) throw error;
    return { url: pathToFileURL(file).href, shortCircuit: true };
  }
}
export async function load(url, context, nextLoad) {
  if (!url.startsWith('file:') || !url.endsWith('.ts')) return nextLoad(url, context);
  const file = fileURLToPath(url);
  if (!inside(file)) throw new Error('Unexpected TypeScript entry');
  return { format: 'module', source: compiler.transpileModule(await fs.readFile(file, 'utf8'), {
    fileName: file, compilerOptions: { target: compiler.ScriptTarget.ES2022, module: compiler.ModuleKind.ESNext },
  }).outputText, shortCircuit: true };
}
