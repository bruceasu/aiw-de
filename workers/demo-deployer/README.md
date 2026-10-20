# Java deployment workflow demo

This package is independent of `process-typescript`. It demonstrates a workflow defined by TypeScript processes and submitted through one generic Control Plane API. The existing software-development workflow does not deploy applications.

| ID | Responsibility |
| --- | --- |
| `demo-java-deploy@v1` | Workflow identity; no running `demo-deployer` worker is needed |
| `demo-deploy-acceptor` | Claims the intake task from the Control Plane, validates the deployment request, and proposes the two fixed steps |
| `demo-deploy-packager` | Checks out the requested commit and runs `mvn -DskipTests package` |
| `demo-deploy-uploader` | Verifies the packaged artifact and copies it to the requested directory |

Clients send requests to the Control Plane. It creates an intake task for `demo-deploy-acceptor`. The acceptor claims that task through Worker Protocol and submits the two fixed steps back to the Control Plane. The package step depends on completion of intake, and upload depends on package. The Control Plane owns the task graph, dependencies, leases and status. Adding another workflow requires another acceptor and its execution workers, without another Java controller or `TaskType` value. Human review of the worker code and its deployment remains the workflow publication step.

## Run locally

Requirements: Node.js 22+, Git, Maven, a JDK compatible with the target program, and a local Maven project that builds one jar in `target/`. Run `npm ci` and `npm run build` in this directory. Create separate staging and deployment directories. The packager and uploader must see the **same staging directory**.

Enable the generic submission API on the Control Plane and provide a private token:

```powershell
$env:AIDEV_EXTERNAL_WORKFLOW_ENABLED = 'true'
$env:AIW_EXTERNAL_WORKFLOW_REQUEST_TOKEN = '<client secret>'
$env:AIW_EXTERNAL_WORKFLOW_TOKEN = '<different internal secret>'
```

Start the three processes in separate terminals with common settings:

```powershell
$env:AIW_CONTROL_PLANE_URL = 'http://127.0.0.1:8080'
$env:AIW_EXTERNAL_WORKFLOW_TOKEN = '<internal secret>'  # acceptor only
$env:AIW_DEPLOY_STAGING_ROOT = 'D:\deploy-demo\staging'  # packager and uploader
$env:AIW_DEPLOY_ALLOWED_ROOT = 'D:\deploy-demo\releases' # uploader only
$env:AIW_REPOSITORY_REF = 'demo-app'              # packager only
$env:AIW_REPOSITORY_ROOT = 'D:\work\demo-app'    # packager only
$env:AIW_MAVEN_EXECUTABLE = 'D:\tools\apache-maven\bin\mvn.cmd' # packager only

npm run start:acceptor  # terminal 1, claims intake tasks from Control Plane
npm run start:packager  # terminal 2
npm run start:uploader  # terminal 3
```

Submit a request directly to the Control Plane. Use a full commit ID and an existing directory inside `AIW_DEPLOY_ALLOWED_ROOT`:

```powershell
$body = @{
  repositoryRef = 'demo-app'
  commit = '0123456789abcdef0123456789abcdef01234567'
  deployDirectory = 'D:\deploy-demo\releases\app'
  artifactPath = 'target/demo-app.jar' # optional if target contains exactly one jar
} | ConvertTo-Json
$request = @{ capability = 'demo-deploy-acceptor'; instruction = $body } | ConvertTo-Json
$headers = @{ 'X-Workflow-Token' = $env:AIW_EXTERNAL_WORKFLOW_REQUEST_TOKEN }
$run = Invoke-RestMethod 'http://127.0.0.1:8080/api/r1/workflows/demo-java-deploy@v1/requests' -Method Post -Headers $headers -ContentType 'application/json' -Body $request
Invoke-RestMethod "http://127.0.0.1:8080/api/r1/workflows/demo-java-deploy@v1/runs/$($run.runId)" -Headers $headers
```

The intake task becomes `READY` first. After it submits the fixed steps and completes, the package task becomes `READY`. The upload task remains `BLOCKED` until packaging completes. The packager writes a versioned jar and SHA-256 record to staging; the uploader verifies both before copying the jar and its hash record to the target directory. Repeated execution accepts an existing artifact only if the hash record matches. The demo does not restart the application, change a `current` pointer, or roll back a release.

The generic submission API is disabled by default. Client and internal submission tokens are separate. The internal token grants permission to submit bounded task graphs; issue it only to reviewed acceptor processes. The API checks graph order, step count, task ownership and dependency validity; it does not inspect the business meaning of each workflow. A production deployment flow still needs workflow-scoped client permissions, an approved workflow registry, and deployment approval.
