import path from "node:path";
import { uploadJava } from "./java-deployment.js";
import { required, runProtocolWorker } from "./worker-protocol.js";

const stagingRoot = path.resolve(required("AIW_DEPLOY_STAGING_ROOT"));
const allowedDeployRoot = path.resolve(required("AIW_DEPLOY_ALLOWED_ROOT"));
await runProtocolWorker("demo-deploy-uploader", "demo-deploy-upload", [],
  (task, signal) => uploadJava(task, { stagingRoot, allowedDeployRoot }, signal));
