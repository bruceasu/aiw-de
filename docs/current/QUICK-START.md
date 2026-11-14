# Quick start: local Plane, Worker and private Secretary

Use JDK 25, Maven 3.9+ and Python 3.11+. Prepare the selected Python environment with the existing [Worker requirements](../../workers/process-python/requirements.txt) and, when needed, [Secretary requirements](../../personal-secretary/requirements.txt). Start with text work; a Git repository, workspace and Codex CLI are optional.

## Start the local Plane

Create `data` in the project root and edit the root `.env`:

```dotenv
AIW_DB_URL=jdbc:sqlite:./data/aiw.db
AIW_BIND_ADDRESS=127.0.0.1
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=replace-with-your-own-admin-password
```

The database directory must exist. The default active profile is `sqlite`, with the built-in URL `jdbc:sqlite:d:/temp/data/aiwde.db`. Set `AIW_DB_URL` explicitly as above so the path works on your device. Switching to PostgreSQL also requires the matching profile, driver and database mode; changing only the URL under the SQLite profile is insufficient. The password configures the Plane administrator and the Owner used for local Secretary enrollment; the Secretary does not ask you to enter that password again.

Windows PowerShell, from the project root:

```powershell
New-Item -ItemType Directory -Force data | Out-Null
.\scripts\startup.bat
```

Linux / macOS Bash, from the project root:

```bash
mkdir -p data
bash scripts/startup.sh
```

Both startup scripts load the root `.env`. If you use `mvn spring-boot:run` directly, set the same variables in that process environment; Maven does not load this file. Open `http://127.0.0.1:8080/ui`. Startup applies database migrations; preserve a backup before upgrading an existing database.

## Connect a model and start work

In Agent factory, choose the connection, model and execution method. API keys can come from `OPENAI_API_KEY` or `DEEPSEEK_API_KEY` in the Python process environment; compatible services let you choose the variable name in settings. Select a listed model or enter its name. Use the existing sign-in entry for a ChatGPT connection. Codex app-server is a fallback Runtime.

The factory starts local Workers when needed. A remote Worker manages its own model connection and cannot assume access to credential files on the Plane device. If work fails, inspect the reason and existing results before choosing retry or repair.

- Use Simple task for a direct request and explicitly start it.
- Use Wish Pool to clarify an idea or optional source attachment with a specialist, save confirmed requirements and generate a workflow draft.
- Use Workflows for task, LLM decision and human-question nodes. Review the draft before starting business work.
- Use Workers to observe actual work, and Pending work for questions and approvals. Stopping new claims lets current work continue. Marking a Worker offline does not terminate its remote process.

## Run the private Secretary independently

In another terminal, using the Python environment prepared for the Secretary:

```sh
python personal-secretary/main.py
```

The default UI is `http://127.0.0.1:8766`; private data lives in `.aiw-personal` under the user directory. Select a model in settings to chat without connecting to the Plane. The Secretary loads `personal-secretary/.env`, not the Plane root `.env`, whether started directly or through its existing start scripts.

Select the local Plane loopback address for automatic enrollment to its configured administrator Owner. No separate Owner login is required. Only explicitly selected and confirmed requirements or tasks are sent; private conversation history is not uploaded automatically.

Pausing automatic starts still permits conversation and receipt updates. Pause this run and cancel further operations are separate controls. Shortcuts fill the composer without sending. The conversation log searches titles and summaries; reply buttons show actual run records. Switching conversations keeps unsent text on this page, but reloading the page does not preserve drafts.

## Other devices and remote access

Keep local mode bound to `127.0.0.1`. For remote Secretary connections, follow [Agent mTLS deployment](../features/archive/FD-072/AGENT_MTLS_DEPLOYMENT.md) for HTTPS, trust and client certificates. It includes Windows PowerShell and Linux / macOS certificate-generation, file-permission and startup steps.

Create a dedicated Agent pairing invitation in the Plane, approve the remote Secretary identity and enable its new process. Select REMOTE rather than local trust. Ordinary remote Workers use administrator-issued per-Worker credentials; see the [Python Worker guide](../../workers/process-python/README.md). Configure models on each device; a file path does not share credentials across devices.

These are operating instructions. This delivery used compilation and static review only; services, model login, network calls and deployment acceptance were not run.
