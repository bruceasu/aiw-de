# OpenAI Agents SDK compatibility demo worker

This isolated worker makes one Responses API request through the OpenAI Agents
SDK and exits. It does not register with the AIW Control Plane or access a
workspace. Tracing is disabled. API credentials are read only from environment
variables and are never written to files.

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:OPENAI_BASE_URL = "http://127.0.0.1:43127/v1"
$env:OPENAI_API_KEY = "<your-token>"
$env:AIW_OPENAI_MODEL = "gpt-6-luna"
python .\agent_worker.py
```

Set `OPENAI_BASE_URL` to the compatible service's API base URL. The demo uses
the Agents SDK `OpenAIProvider` with `use_responses=True`, so the endpoint must
implement `POST /responses` and return a completed Responses API result. A
Chat Completions-only endpoint is insufficient. Success prints
`status=ok`; errors report only the exception type to avoid exposing secrets
or request contents.

## Tool-call acceptance case

After the basic response check passes, run:

```powershell
python .\test_tool_call.py
```

The agent must invoke the local `add_numbers` function exactly once with
`a=19` and `b=23`, then return `TOOL_CALL_OK:42`. The check passes only if the
SDK executed that function call and returned the expected final marker. This
exercises the Responses API function-call cycle; it does not test streaming,
multiple tools, cancellation, or AIW worker registration.
