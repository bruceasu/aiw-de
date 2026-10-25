import { mkdir } from "node:fs/promises";
import path from "node:path";
import { packageJava } from "./java-deployment.js";
import { required, runProtocolWorker } from "./worker-protocol.js";

const repositoryRef = required("AIW_REPOSITORY_REF");
const repositoryRoot = path.resolve(required("AIW_REPOSITORY_ROOT"));
const stagingRoot = path.resolve(required("AIW_DEPLOY_STAGING_ROOT"));
const worktreeRoot = path.resolve(process.env.AIW_DEPLOY_WORKTREE_ROOT || path.join(repositoryRoot, "..", ".aidev-deploy-worktrees"));
await mkdir(stagingRoot, { recursive: true });
await mkdir(worktreeRoot, { recursive: true });
await runProtocolWorker("demo-deploy-packager", "demo-deploy-package", [`repo:${repositoryRef}`],
  (task, signal) => packageJava(task, {
    repositoryRef, repositoryRoot, stagingRoot, worktreeRoot,
    gitExecutable: process.env.AIW_GIT_EXECUTABLE || "git",
    mavenExecutable: process.env.AIW_MAVEN_EXECUTABLE || (process.platform === "win32" ? "mvn.cmd" : "mvn"),
  }, signal));
