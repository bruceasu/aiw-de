import type { DeploymentResult, DeploymentTask } from "./java-deployment.js";

export interface ClaimedTask extends DeploymentTask {
  leaseToken: string;
  subjectType: string;
  subjectId: string;
}

export function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

const baseUrl = (process.env.AIW_CONTROL_PLANE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const token = process.env.AIW_WORKER_TOKEN || "";

async function post<T>(endpoint: string, body: unknown): Promise<T | null> {
  const response = await fetch(`${baseUrl}/api/r1/worker-protocol/v1${endpoint}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15_000),
  });
  if (response.status === 204) return null;
  if (!response.ok) throw new Error(`Worker Protocol ${response.status}: ${await response.text()}`);
  const content = await response.text();
  return content ? JSON.parse(content) as T : null;
}

export async function runProtocolWorker(
  workerId: string, capability: string, extraCapabilities: string[],
  execute: (task: ClaimedTask, signal: AbortSignal) => Promise<DeploymentResult>,
): Promise<void> {
  let stopping = false;
  for (const signal of ["SIGINT", "SIGTERM"] as const) process.on(signal, () => { stopping = true; });
  await post("/workers/register", {
    workerId, runtimeType: `demo-typescript:${capability}`,
    capabilities: [capability, ...extraCapabilities], maxConcurrency: 1,
  });
  console.log(`registered ${workerId} capability=${capability}`);
  const workerHeartbeat = setInterval(() => {
    void post(`/workers/${encodeURIComponent(workerId)}/heartbeat`, {})
      .catch(error => console.error("worker heartbeat failed", error));
  }, 20_000);
  try {
    while (!stopping) {
      try {
        const task = await post<ClaimedTask>("/tasks/claim", { workerId });
        if (task) await runTask(workerId, task, execute);
        else await new Promise(resolve => setTimeout(resolve, 1000));
      } catch (error) {
        console.error("worker polling failed", error);
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
    }
  } finally {
    clearInterval(workerHeartbeat);
    await post(`/workers/${encodeURIComponent(workerId)}/drain`, {}).catch(() => {});
    await post(`/workers/${encodeURIComponent(workerId)}/offline`, {}).catch(() => {});
  }
}

async function runTask(workerId: string, task: ClaimedTask,
                       execute: (task: ClaimedTask, signal: AbortSignal) => Promise<DeploymentResult>): Promise<void> {
  await post(`/tasks/${encodeURIComponent(task.taskId)}/start`, { workerId, leaseToken: task.leaseToken });
  const abort = new AbortController();
  let lostLease = false;
  let heartbeatBusy = false;
  const heartbeat = setInterval(() => {
    if (heartbeatBusy || lostLease) return;
    heartbeatBusy = true;
    void post(`/tasks/${encodeURIComponent(task.taskId)}/heartbeat`, { workerId, leaseToken: task.leaseToken })
      .catch(error => { lostLease = true; abort.abort(); console.error("lease heartbeat failed", error); })
      .finally(() => { heartbeatBusy = false; });
  }, 20_000);
  try {
    const result = await execute(task, abort.signal);
    if (lostLease) return;
    await post(`/tasks/${encodeURIComponent(task.taskId)}/complete`, {
      workerId, leaseToken: task.leaseToken,
      workspaceResult: task.workspace ? { resultRevision: result.commit, changedFiles: [] } : null,
      report: { verdict: "PASS", summary: JSON.stringify(result) },
    });
    console.log(`completed ${task.taskId} artifact=${result.artifact} sha256=${result.sha256}`);
  } catch (error) {
    if (!lostLease) {
      try {
        await post(`/tasks/${encodeURIComponent(task.taskId)}/fail`, {
          workerId, leaseToken: task.leaseToken, failureReason: String(error),
        });
      } catch (reportError) { console.error("failure report failed", reportError); }
    }
    console.error(`task ${task.taskId} failed`, error);
  } finally {
    clearInterval(heartbeat);
  }
}
