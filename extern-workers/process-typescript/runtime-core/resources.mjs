import path from "node:path";
import { lstat, realpath } from "node:fs/promises";
import { RuntimeFault, checkSignal, errorCode } from "./errors.mjs";

export function isInside(root, target, allowRoot = false) {
  const relative = path.relative(path.resolve(root), path.resolve(target));
  return (allowRoot || relative !== "") && relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

export async function safePath(root, relative, write = false) {
  if (typeof relative !== "string" || relative.includes("\0")) throw new RuntimeFault("PATH_INVALID");
  const target = path.resolve(root, relative);
  if (!isInside(root, target, !write)) throw new RuntimeFault("PATH_OUTSIDE_WORKSPACE");
  const parts = path.relative(root, target).split(path.sep).filter(Boolean);
  if (parts.some(part => part.toLowerCase() === ".git")) throw new RuntimeFault("GIT_METADATA_FORBIDDEN");
  let cursor = root;
  for (const part of parts) {
    cursor = path.join(cursor, part);
    try {
      const stat = await lstat(cursor);
      if (stat.isSymbolicLink()) throw new RuntimeFault("SYMLINK_FORBIDDEN");
      if (write && stat.isFile() && stat.nlink > 1) throw new RuntimeFault("HARDLINK_WRITE_FORBIDDEN");
    } catch (error) {
      if (write && error.code === "ENOENT") break;
      throw error;
    }
  }
  return target;
}

const compatible = {
  IS: new Set(["IS", "IX", "S"]), IX: new Set(["IS", "IX"]), S: new Set(["IS", "S"]), X: new Set(),
};
function conflict(a, b) {
  if (!compatible[a.mode]?.has(b.mode)) return true;
  return (a.files ?? []).some(x => (b.files ?? []).some(y => x.key === y.key && (x.mode === "X" || y.mode === "X")));
}
const coordinators = new Map();
const collected = new FinalizationRegistry(key => { if (!coordinators.get(key)?.deref()) coordinators.delete(key); });
// An unresolved native process must keep its leases even after its caller returns.
const unresolvedCoordinators = new Set();

export class ResourceCoordinator {
  #active = new Set();
  #queue = [];
  #idle = [];
  #uncertain = false;
  constructor({ maxActive = 4, maxQueue = 64 } = {}) {
    this.maxActive = maxActive;
    this.maxQueue = maxQueue;
    if (!Number.isSafeInteger(maxActive) || maxActive < 1 || !Number.isSafeInteger(maxQueue) || maxQueue < 1) throw new RuntimeFault("RESOURCE_LIMIT_INVALID", "CONFIGURATION");
  }
  static async forWorkspace(cwd, options) {
    let key = await realpath(cwd);
    if (process.platform === "win32") key = key.toLowerCase();
    let coordinator = coordinators.get(key)?.deref();
    if (!coordinator) {
      coordinator = new ResourceCoordinator(options);
      coordinators.set(key, new WeakRef(coordinator));
      collected.register(coordinator, key);
    }
    return coordinator;
  }
  static fileKey(file) { return process.platform === "win32" ? path.resolve(file).toLowerCase() : path.resolve(file); }
  async run(request, signal, operation) {
    checkSignal(signal);
    if (this.#uncertain) throw new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN");
    if (!compatible[request.mode]) throw new RuntimeFault("RESOURCE_MODE_INVALID", "CONFIGURATION");
    if (this.#queue.length >= this.maxQueue) throw new RuntimeFault("RESOURCE_QUEUE_FULL", "TOOL_ERROR", false);
    const item = { ...request, signal };
    await new Promise((resolve, reject) => {
      item.resolve = resolve;
      item.reject = reject;
      item.cancel = () => {
        const index = this.#queue.indexOf(item);
        if (index >= 0) {
          this.#queue.splice(index, 1);
          signal?.removeEventListener("abort", item.cancel);
          reject(new RuntimeFault("RESOURCE_WAIT_CANCELLED"));
          this.#pump();
        }
      };
      this.#queue.push(item);
      signal?.addEventListener("abort", item.cancel, { once: true });
      this.#pump();
    });
    let retained = false;
    try { checkSignal(signal); return await operation(); }
    catch (error) {
      if (["PROCESS_TREE_UNCERTAIN", "RESOURCE_TERMINATION_UNCERTAIN"].includes(errorCode(error))) {
        retained = true;
        this.#uncertain = true;
        unresolvedCoordinators.add(this);
        for (const queued of this.#queue.splice(0)) {
          queued.signal?.removeEventListener("abort", queued.cancel);
          queued.reject(new RuntimeFault("RESOURCE_TERMINATION_UNCERTAIN"));
        }
      }
      throw error;
    }
    finally {
      if (!retained) this.#active.delete(item);
      this.#pump();
    }
  }
  #pump() {
    if (this.#uncertain) return;
    for (let index = 0; index < this.#queue.length && this.#active.size < this.maxActive;) {
      const item = this.#queue[index];
      const blocked = [...this.#active].some(active => conflict(item, active))
        || this.#queue.slice(0, index).some(earlier => conflict(item, earlier));
      if (blocked) { index++; continue; }
      this.#queue.splice(index, 1);
      item.signal?.removeEventListener("abort", item.cancel);
      this.#active.add(item);
      item.resolve();
    }
    if (!this.#active.size && !this.#queue.length) for (const resolve of this.#idle.splice(0)) resolve();
  }
  async settled() {
    if (this.#active.size || this.#queue.length) await new Promise(resolve => this.#idle.push(resolve));
  }
}
