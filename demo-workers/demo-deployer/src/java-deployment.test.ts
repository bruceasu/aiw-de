import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { parseDeploymentInput, permittedDirectory, uploadJava } from "./java-deployment.js";

test("deployment input requires an absolute destination and bounded jar path", () => {
  assert.throws(() => parseDeploymentInput(JSON.stringify({ deployDirectory: "relative" })), /absolute/);
  assert.throws(() => parseDeploymentInput(JSON.stringify({ deployDirectory: path.resolve("deploy"), artifactPath: "../other.jar" })), /relative jar/);
  assert.deepEqual(parseDeploymentInput(JSON.stringify({ deployDirectory: path.resolve("deploy"), artifactPath: "target/app.jar" })), {
    deployDirectory: path.resolve("deploy"), artifactPath: "target/app.jar",
  });
});

test("uploader copies the packaged artifact and accepts a verified retry", async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), "aiw-deploy-upload-"));
  try {
    const stage = path.join(temporary, "stage");
    const destination = path.join(temporary, "destination");
    await mkdir(stage);
    await mkdir(destination);
    const commit = "a".repeat(40);
    const artifact = path.join(stage, `app-${commit}.jar`);
    await writeFile(artifact, "demo jar bytes");
    const digest = createHash("sha256").update("demo jar bytes").digest("hex");
    const task = { taskId: "WF-test~upload", taskType: "EXTERNAL_WORK", skillRef: "demo-deploy-upload",
      instruction: JSON.stringify({ deployDirectory: destination }), workspace: null,
      inputs: [{ taskId: "WF-test~package", taskType: "EXTERNAL_WORK",
        completionSummary: JSON.stringify({ commit, artifact, sha256: digest }) }] };
    const cfg = { stagingRoot: stage, allowedDeployRoot: destination };
    const first = await uploadJava(task, cfg, new AbortController().signal);
    const second = await uploadJava(task, cfg, new AbortController().signal);
    assert.equal(first.artifact, path.join(destination, `app-${commit}.jar`));
    assert.deepEqual(second, first);
    assert.equal(await readFile(first.artifact, "utf8"), "demo jar bytes");
    await writeFile(first.artifact, "tampered bytes");
    await assert.rejects(uploadJava(task, cfg, new AbortController().signal), /hash record/);
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});

test("deployment destination must stay under the worker allow root, including symlinks", async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), "aiw-deploy-path-"));
  try {
    const root = path.join(temporary, "allowed");
    const inside = path.join(root, "app");
    const outside = path.join(temporary, "outside");
    await mkdir(inside, { recursive: true });
    await mkdir(outside);
    assert.equal(await permittedDirectory(root, inside), inside);
    await assert.rejects(permittedDirectory(root, outside), /outside/);
    const link = path.join(root, "link");
    await symlink(outside, link, "junction");
    await assert.rejects(permittedDirectory(root, link), /outside/);
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});
