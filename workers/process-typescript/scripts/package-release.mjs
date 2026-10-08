import { access, cp, mkdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const { version } = JSON.parse(await readFile(path.join(packageRoot, "package.json"), "utf8"));
const releaseRoot = path.join(packageRoot, "out");
const releaseDirectory = path.join(releaseRoot, `process-typescript-${version}`);
const releaseFiles = [
  "dist",
  "runtime-core",
  "bin",
  "profiles",
  "skills",
  "aiw-tools.example.json",
  "scripts/run-role.ps1",
  "scripts/run-role.sh",
  "package.json",
  "package-lock.json",
  ".npmrc",
  "README.md",
];

for (const relativePath of releaseFiles) {
  await access(path.join(packageRoot, relativePath));
}

try {
  await stat(releaseDirectory);
  throw new Error(`Release already exists: ${releaseDirectory}. Remove or rename it before rebuilding.`);
} catch (error) {
  if (error.code !== "ENOENT") throw error;
}

await mkdir(releaseDirectory, { recursive: true });
for (const relativePath of releaseFiles) {
  const destination = path.join(releaseDirectory, relativePath);
  await mkdir(path.dirname(destination), { recursive: true });
  await cp(path.join(packageRoot, relativePath), destination, { recursive: true });
}
console.log(`Release directory: ${releaseDirectory}`);
console.log("On the destination machine: npm ci --omit=dev --omit=optional; select AIW_AGENT_RUNTIME=codex-sdk or openai-agents.");
