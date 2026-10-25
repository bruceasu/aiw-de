import { existsSync } from "node:fs";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("./", import.meta.url));
const compiler = fileURLToPath(new URL("./node_modules/typescript/bin/tsc", import.meta.url));
if (!existsSync(compiler)) {
  console.error("TypeScript is missing. Run npm ci --omit=optional in the worker directory first.");
  process.exitCode = 1;
} else {
  const child = spawn(process.execPath, [compiler, "-p", "tsconfig.json"], {
    cwd: root, stdio: "inherit",
  });
  child.on("error", error => { console.error(error.message); process.exitCode = 1; });
  child.on("exit", (code, signal) => { process.exitCode = code ?? (signal ? 1 : 0); });
}
