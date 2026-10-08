import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

let allowed, compiler;
export async function initialize({ repository }) {
  allowed = path.join(repository, 'workers', 'process-typescript-with-api', 'src');
  compiler = createRequire(path.join(repository, 'workers', 'process-typescript-with-api', 'package.json'))('typescript');
}
const isAllowed = filename => {
  const relative = path.relative(allowed, filename);
  return relative && !relative.startsWith('..') && !path.isAbsolute(relative);
};
export async function resolve(specifier, context, nextResolve) {
  try { return await nextResolve(specifier, context); }
  catch (error) {
    if (!context.parentURL?.startsWith('file:') || !specifier.startsWith('.') || !specifier.endsWith('.js')) throw error;
    const filename = fileURLToPath(new URL(specifier.slice(0, -3) + '.ts', context.parentURL));
    if (!isAllowed(filename) || !(await fs.stat(filename).catch(() => undefined))?.isFile()) throw error;
    return { url: pathToFileURL(filename).href, shortCircuit: true };
  }
}
export async function load(url, context, nextLoad) {
  if (!url.startsWith('file:') || !url.endsWith('.ts')) return nextLoad(url, context);
  const filename = fileURLToPath(url);
  if (!isAllowed(filename) || path.basename(filename) === 'index.ts') throw new Error('Unexpected TypeScript entry');
  const source = await fs.readFile(filename, 'utf8');
  return { format: 'module', source: compiler.transpileModule(source, {
    fileName: filename,
    compilerOptions: { target: compiler.ScriptTarget.ES2022, module: compiler.ModuleKind.ESNext },
  }).outputText, shortCircuit: true };
}
