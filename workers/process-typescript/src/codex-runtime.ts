import { accessSync, constants, realpathSync, statSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

interface Executable {
  executablePath: string;
  pathDirectories: string[];
}

/** Resolve the installed CLI before registering; never fall back to the SDK's local binary. */
export function resolveInstalledCodex(override?: string): { executablePath: string; env: Record<string, string> } {
  const requested = override?.trim() || "codex";
  const explicitPath = path.isAbsolute(requested) || /[/\\]/.test(requested);
  const workerDependencies = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../node_modules");
  const pathKey = Object.keys(process.env).find(key => key.toUpperCase() === "PATH") ?? "PATH";
  const candidates = explicitPath ? [path.resolve(requested)]
    : (process.env[pathKey] ?? "").split(path.delimiter).filter(Boolean).flatMap(directory => {
      const base = path.resolve(directory.replace(/^"(.*)"$/, "$1"));
      // npm start prepends this worker's node_modules/.bin, which contains the SDK's CLI shim.
      if (!override?.trim() && within(workerDependencies, base)) return [];
      const names = process.platform === "win32" && path.extname(requested) === ""
        ? [`${requested}.exe`, `${requested}.cmd`, `${requested}.ps1`] : [requested];
      return names.map(name => path.join(base, name));
    });

  for (const candidate of candidates) {
    const executable = resolveLauncher(candidate);
    if (!executable) continue;
    if (!override?.trim() && within(workerDependencies, realpathSync(executable.executablePath))) continue;
    const env: Record<string, string> = {};
    for (const [key, value] of Object.entries(process.env)) {
      if (value !== undefined && key.toUpperCase() !== "PATH") env[key] = value;
    }
    env[pathKey] = [...executable.pathDirectories, process.env[pathKey] ?? ""].join(path.delimiter);
    return { executablePath: executable.executablePath, env };
  }
  throw new Error(`Installed Codex CLI not found (${requested}). Add your existing Codex installation to PATH or set AIW_CODEX_PATH to its executable. On Windows, npm codex.cmd/codex.ps1 launchers must have their native codex.exe installed. No bundled CLI fallback is used.`);
}

function resolveLauncher(candidate: string): Executable | null {
  if (!isFile(candidate)) return null;
  if (process.platform !== "win32") {
    try {
      accessSync(candidate, constants.X_OK);
      return { executablePath: candidate, pathDirectories: [] };
    } catch { return null; }
  }
  if (path.extname(candidate).toLowerCase() === ".exe") {
    return { executablePath: candidate, pathDirectories: companionPaths(path.dirname(path.dirname(candidate))) };
  }

  // Node's spawn cannot execute a Windows npm .cmd/.ps1 shim directly without a shell.
  const packageRoots = [path.join(path.dirname(candidate), "node_modules", "@openai", "codex")];
  if (path.extname(candidate).toLowerCase() === ".js") {
    packageRoots.unshift(path.dirname(path.dirname(realpathSync(candidate))));
  }
  for (const packageRoot of packageRoots) {
    const executable = nativeNpmExecutable(packageRoot);
    if (executable) return executable;
  }
  return null;
}

function nativeNpmExecutable(packageRoot: string): Executable | null {
  const manifest = path.join(packageRoot, "package.json");
  if (!isFile(manifest)) return null;
  const target = process.arch === "x64" ? "x86_64-pc-windows-msvc"
    : process.arch === "arm64" ? "aarch64-pc-windows-msvc" : null;
  if (!target) return null;
  const vendorRoots: string[] = [];
  try {
    const platformManifest = createRequire(manifest).resolve(`@openai/codex-win32-${process.arch}/package.json`);
    vendorRoots.push(path.join(path.dirname(platformManifest), "vendor"));
  } catch { /* Older CLI packages keep vendor files in the main package. */ }
  vendorRoots.push(path.join(packageRoot, "vendor"));
  for (const vendorRoot of vendorRoots) {
    const targetRoot = path.join(vendorRoot, target);
    for (const directory of ["bin", "codex"]) {
      const executablePath = path.join(targetRoot, directory, "codex.exe");
      if (isFile(executablePath)) return { executablePath, pathDirectories: companionPaths(targetRoot) };
    }
  }
  return null;
}

function companionPaths(targetRoot: string): string[] {
  return ["codex-path", "path"].map(directory => path.join(targetRoot, directory)).filter(directory => {
    try { return statSync(directory).isDirectory(); } catch { return false; }
  });
}

function isFile(candidate: string): boolean {
  try { return statSync(candidate).isFile(); } catch { return false; }
}

function within(root: string, candidate: string): boolean {
  const relative = path.relative(root, candidate);
  return relative === "" || (!path.isAbsolute(relative) && relative !== ".." && !relative.startsWith(`..${path.sep}`));
}
