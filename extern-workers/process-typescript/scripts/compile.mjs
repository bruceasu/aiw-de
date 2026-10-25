import { createRequire } from "node:module";
import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Syntax compilation only: keep emitted JavaScript in memory, with no artifact.
const require = createRequire(import.meta.url);
const ts = require(process.env.AIW_TYPESCRIPT_COMPILER || "typescript");
const source = fileURLToPath(new URL("../src/", import.meta.url));
let failed = false;
for (const file of (await readdir(source)).filter(file => file.endsWith(".ts"))) {
  const result = ts.transpileModule(await readFile(`${source}/${file}`, "utf8"), {
    fileName: file,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.NodeNext, isolatedModules: true },
    reportDiagnostics: true,
  });
  for (const diagnostic of result.diagnostics || []) {
    console.error(ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n"));
    if (diagnostic.category === ts.DiagnosticCategory.Error) failed = true;
  }
}
if (failed) process.exitCode = 1;
else console.log("TypeScript syntax compilation passed (no artifacts; no dependency/type validation).");
