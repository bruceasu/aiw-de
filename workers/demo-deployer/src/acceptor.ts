import path from "node:path";
import { runProtocolWorker } from "./worker-protocol.js";

const workflowId = "demo-java-deploy@v1";
const controlPlane = (process.env.AIW_CONTROL_PLANE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const submissionToken = process.env.AIW_EXTERNAL_WORKFLOW_TOKEN || "";
if (!submissionToken) throw new Error("AIW_EXTERNAL_WORKFLOW_TOKEN is required");

function deploymentSteps(instruction: string): unknown {
  const input: unknown = JSON.parse(instruction);
  if (!input || typeof input !== "object" || Array.isArray(input)) throw new Error("invalid deployment request");
  const data = input as Record<string, unknown>;
  if (typeof data.repositoryRef !== "string" || !/^[A-Za-z0-9._-]{1,120}$/.test(data.repositoryRef) ||
      typeof data.commit !== "string" || !/^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(data.commit) ||
      typeof data.deployDirectory !== "string" || !path.isAbsolute(data.deployDirectory) ||
      (data.artifactPath != null && (typeof data.artifactPath !== "string" ||
        path.isAbsolute(data.artifactPath) || data.artifactPath.split(/[\\/]/).includes("..") ||
        !data.artifactPath.endsWith(".jar")))) throw new Error("invalid deployment request");
  return { steps: [
    { key: "package", capability: "demo-deploy-package",
      instruction: JSON.stringify({ artifactPath: data.artifactPath || null }),
      repositoryRef: data.repositoryRef, baseRevision: data.commit, dependsOn: [] },
    { key: "upload", capability: "demo-deploy-upload",
      instruction: JSON.stringify({ deployDirectory: data.deployDirectory }), dependsOn: ["package"] },
  ] };
}

await runProtocolWorker("demo-deploy-acceptor", "demo-deploy-acceptor", [], async task => {
  if (task.taskType !== "EXTERNAL_WORK" || task.skillRef !== "demo-deploy-acceptor" ||
      task.subjectType !== workflowId || !/^WF-[a-f0-9]{32}$/.test(task.subjectId) ||
      task.taskId !== `${task.subjectId}~intake`) throw new Error("unexpected intake task");
  const response = await fetch(`${controlPlane}/api/workflows/${workflowId}/runs/${task.subjectId}/steps`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Workflow-Token": submissionToken },
    body: JSON.stringify(deploymentSteps(task.instruction)),
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`step submission failed: ${response.status} ${await response.text()}`);
  return { commit: "", artifact: "", sha256: "" };
});
