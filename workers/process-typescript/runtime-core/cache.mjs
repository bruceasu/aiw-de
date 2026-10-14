import { createHash } from "node:crypto";
import { lstatSync, mkdirSync, mkdtempSync, realpathSync } from "node:fs";
import path from "node:path";
import { RuntimeFault } from "./errors.mjs";

export const TOOL_CACHE_ENV_NAMES = ["TMPDIR", "TMP", "TEMP", "XDG_CACHE_HOME", "npm_config_cache",
  "NPM_CONFIG_CACHE", "GOCACHE", "GOMODCACHE", "GOTMPDIR", "PIP_CACHE_DIR", "JAVA_TOOL_OPTIONS"];

export function toolCacheEnvironment(environment = process.env) {
  return Object.fromEntries(TOOL_CACHE_ENV_NAMES.filter(name => environment[name] !== undefined)
    .map(name => [name, environment[name]]));
}

/** Persistent tool caches per Worker; temporary files get a unique process directory. */
export function prepareToolCache(workerId) {
  const configured = process.env.AIW_WORKER_CACHE_ROOT || "/mnt/temp/cache";
  if (!path.isAbsolute(configured) || /[\r\n"\0]/.test(configured)) {
    throw new RuntimeFault("WORKER_CACHE_ROOT_INVALID", "CONFIGURATION");
  }
  try {
    mkdirSync(configured, { recursive: true, mode: 0o700 });
    const root = realpathSync(configured);
    const readable = workerId.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80) || "worker";
    const digest = createHash("sha256").update(workerId).digest("hex").slice(0, 12);
    const directory = path.join(root, "workers", `${readable}-${digest}`);
    const ensure = relative => {
      let current = root;
      for (const part of path.relative(root, path.join(directory, relative)).split(path.sep)) {
        current = path.join(current, part);
        try { mkdirSync(current, { mode: 0o700 }); }
        catch (error) { if (error.code !== "EEXIST") throw error; }
        if (!lstatSync(current).isDirectory() || lstatSync(current).isSymbolicLink()) throw new Error("Unsafe cache directory");
      }
      return current;
    };
    const temp = mkdtempSync(path.join(ensure("tmp"), `${process.pid}-`));
    const repository = ensure("maven/repository");
    const jvmOptions = `${process.env.JAVA_TOOL_OPTIONS || ""} -Djava.io.tmpdir="${temp}" -Dmaven.repo.local="${repository}"`.trim();
    const npm = ensure("npm");
    const environment = { TMPDIR: temp, TMP: temp, TEMP: temp, XDG_CACHE_HOME: ensure("xdg"),
      npm_config_cache: npm, NPM_CONFIG_CACHE: npm, GOCACHE: ensure("go/build"),
      GOMODCACHE: ensure("go/modules"), GOTMPDIR: temp, PIP_CACHE_DIR: ensure("python/pip"),
      JAVA_TOOL_OPTIONS: jvmOptions };
    return { directory, temp, environment };
  } catch { throw new RuntimeFault("WORKER_CACHE_SETUP_FAILED", "CONFIGURATION"); }
}
