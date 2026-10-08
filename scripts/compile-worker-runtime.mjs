// Compile production sources only. No tests, SDK execution, model calls or retained distribution.
import { createRequire } from "node:module";
import { readdir, readFile, mkdtemp, rm, rmdir } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import os from "node:os";
import path from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
const require = createRequire(new URL("../workers/process-typescript/package.json", import.meta.url));
const ts = require("typescript");
let failed = false;
for (const worker of ["process-typescript"]) {
  const directory = path.join(root, "workers", worker, "src");
  for (const file of (await readdir(directory)).filter(file => file.endsWith(".ts") && !file.endsWith(".test.ts"))) {
    const result = ts.transpileModule(await readFile(path.join(directory, file), "utf8"), {
      fileName: file, reportDiagnostics: true,
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.NodeNext, isolatedModules: true },
    });
    for (const diagnostic of result.diagnostics ?? []) {
      if (diagnostic.category === ts.DiagnosticCategory.Error) failed = true;
      console.error(`${worker}/${file}: ${ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n")}`);
    }
  }
}
function compile(executable, args, cwd = root, env = process.env) {
  const result = spawnSync(executable, args, { cwd, env, windowsHide: true, encoding: "utf8", maxBuffer: 1024 * 1024, timeout: 120000 });
  if (result.error || result.status !== 0) { failed = true; console.error(result.error?.message ?? result.stderr ?? "compile failed"); }
  return result;
}
for (const directory of [path.join(root, "workers/process-typescript/runtime-core"), path.join(root, "workers/process-typescript/scripts")]) {
  for (const file of (await readdir(directory)).filter(file => file.endsWith(".mjs"))) compile(process.execPath, ["--check", path.join(directory, file)]);
}
const pyDirectory = path.join(root, "workers/process-python");
const pythonFiles = (await readdir(pyDirectory)).filter(file => file.endsWith(".py") && !file.startsWith("test_"));
compile("python", ["-c", "import sys,pathlib; [compile(pathlib.Path(p).read_text(encoding='utf-8-sig'),p,'exec') for p in sys.argv[1:]]", ...pythonFiles.map(file => path.join(pyDirectory, file))], root, { ...process.env, PYTHONDONTWRITEBYTECODE: "1" });
const temporary = await mkdtemp(path.join(os.tmpdir(), "aiw-worker-compile-"));
const artifacts = [];
try {
  for (const worker of ["process-go"]) {
    const artifact = path.resolve(temporary, worker + (process.platform === "win32" ? ".exe" : ""));
    artifacts.push(artifact);
    compile("go", ["build", "-o", artifact, "."], path.join(root, "workers", worker), { ...process.env, GOPROXY: "off", GOSUMDB: "off", GOTOOLCHAIN: "local" });
  }
} finally {
  for (const artifact of artifacts) {
    if (path.dirname(artifact) !== path.resolve(temporary)) throw new Error("compile artifact path escaped temporary directory");
    await rm(artifact, { force: true });
  }
  await rmdir(temporary);
}
if (failed) process.exitCode = 1;
else console.log("Worker compile-only passed: TypeScript syntax, JavaScript syntax, Python syntax, Go compiler. No type check, tests, model/network calls or retained distribution.");
