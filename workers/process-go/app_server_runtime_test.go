package main

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestCodexAppServerFakeLifecycle(t *testing.T) {
	fake := buildFakeCodex(t)
	runtime := NewCodexAppServerRuntime(Config{
		CodexPath: fake, AppServerVersion: "0.163.0",
		ApprovalPolicy: "never", Sandbox: "read-only", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelStart()
	if err := runtime.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	if runtime.cliVersion != "0.163.0" {
		t.Fatalf("expected installed version in evidence, got %q", runtime.cliVersion)
	}
	closeRuntime := func() {
		t.Helper()
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := runtime.Close(ctx); err != nil {
			t.Errorf("close fake App Server: %v", err)
		}
	}
	defer closeRuntime()

	type result struct {
		input   string
		output  string
		session string
		err     error
	}
	results := make(chan result, 2)
	var wait sync.WaitGroup
	for _, input := range []string{"alpha", "beta"} {
		input := input
		wait.Add(1)
		go func() {
			defer wait.Done()
			var session string
			got, err := runtime.Run(context.Background(), AgentRunRequest{
				Workspace: t.TempDir(), Instruction: input,
				OnSession: func(id string) { session = id },
			})
			results <- result{input: input, output: got.Output, session: session, err: err}
		}()
	}
	wait.Wait()
	close(results)
	sessions := map[string]bool{}
	for got := range results {
		if got.err != nil {
			t.Fatalf("run %s: %v", got.input, got.err)
		}
		if got.output != "reply:"+got.input {
			t.Errorf("run %s output = %q", got.input, got.output)
		}
		if got.session == "" || sessions[got.session] {
			t.Errorf("session was empty or reused: %q", got.session)
		}
		sessions[got.session] = true
	}

	cancelCtx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
	defer cancel()
	_, err := runtime.Run(cancelCtx, AgentRunRequest{Workspace: t.TempDir(), Instruction: "cancel"})
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("cancelled run error = %v, want deadline exceeded", err)
	}
	select {
	case <-runtime.Failure():
		t.Fatalf("runtime failed after confirmed cancellation: %v", runtime.FailureError())
	default:
	}
}

func TestCodexAppServerIgnoresLegacyVersionPin(t *testing.T) {
	runtime := NewCodexAppServerRuntime(Config{
		CodexPath: buildFakeCodex(t), AppServerVersion: "0.0.0",
		ApprovalPolicy: "never", Sandbox: "read-only", ReasoningEffort: "low",
	})
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := runtime.Start(ctx); err != nil {
		t.Fatal(err)
	}
	closeCtx, cancelClose := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancelClose()
	if err := runtime.Close(closeCtx); err != nil {
		t.Fatal(err)
	}
}

func TestCodexAppServerDisconnectSignalsFailure(t *testing.T) {
	fake := buildFakeCodex(t)
	runtime := NewCodexAppServerRuntime(Config{
		CodexPath: fake, AppServerVersion: codexAppServerVersion,
		ApprovalPolicy: "never", Sandbox: "read-only", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelStart()
	if err := runtime.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	if err := runtime.cmd.Process.Kill(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-runtime.Failure():
		if runtime.FailureError() == nil {
			t.Fatal("disconnected runtime did not report a cause")
		}
	case <-time.After(5 * time.Second):
		t.Fatal("App Server disconnect was not detected")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := runtime.Close(ctx); err != nil {
		t.Fatalf("close disconnected App Server: %v", err)
	}
}

func TestCodexAppServerUnconfirmedInterruptStopsManagedProcess(t *testing.T) {
	fake := buildFakeCodex(t)
	runtime := NewCodexAppServerRuntime(Config{
		CodexPath: fake, AppServerVersion: codexAppServerVersion,
		ApprovalPolicy: "never", Sandbox: "read-only", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelStart()
	if err := runtime.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		_ = runtime.Close(ctx)
	}()
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	_, err := runtime.Run(ctx, AgentRunRequest{Workspace: t.TempDir(), Instruction: "cancel ignore-interrupt"})
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("unconfirmed cancellation error = %v", err)
	}
	select {
	case <-runtime.done:
	case <-time.After(2 * time.Second):
		t.Fatal("managed App Server process did not stop after unconfirmed interrupt")
	}
}

func TestExecuteTaskStopRequestIsAcknowledged(t *testing.T) {
	fake := buildFakeCodex(t)
	appServer := NewCodexAppServerRuntime(Config{
		CodexPath: fake, AppServerVersion: codexAppServerVersion,
		ApprovalPolicy: "never", Sandbox: "workspace-write", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelStart()
	if err := appServer.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := appServer.Close(ctx); err != nil {
			t.Errorf("close fake App Server after stop request: %v", err)
		}
	}()

	var starts, heartbeats, acknowledgements, completions atomic.Int32
	controlPlane := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		switch {
		case strings.HasSuffix(request.URL.Path, "/start"):
			starts.Add(1)
			response.WriteHeader(http.StatusNoContent)
		case strings.HasSuffix(request.URL.Path, "/heartbeat"):
			heartbeats.Add(1)
			response.Header().Set("Content-Type", "application/json")
			_, _ = io.WriteString(response, `{"stopRequested":true}`)
		case strings.HasSuffix(request.URL.Path, "/stop-acknowledgement"):
			acknowledgements.Add(1)
			response.WriteHeader(http.StatusNoContent)
		case strings.HasSuffix(request.URL.Path, "/complete"):
			completions.Add(1)
			response.WriteHeader(http.StatusNoContent)
		default:
			response.WriteHeader(http.StatusNoContent)
		}
	}))
	defer controlPlane.Close()

	profilePath := filepath.Join("profiles", "coding-executor.toml")
	client := &Client{
		cfg: Config{
			AgentRuntime: "codex-app-server", AppServerVersion: codexAppServerVersion,
			Profile: loadRoleProfile(profilePath), ProfilePath: profilePath,
			SkillsRoot: "skills", BaseURL: controlPlane.URL, WorkerID: "fd014-stop-worker",
			FallbackWorkspace: t.TempDir(), HTTPTimeout: time.Second,
			HeartbeatInterval: 20 * time.Millisecond, CodexPath: fake,
		},
		http: &http.Client{Timeout: time.Second}, runtime: appServer,
		forceCtx: context.Background(),
	}
	evidenceRoot := t.TempDir()
	t.Setenv("AIW_WORKER_EVIDENCE_ROOT", evidenceRoot)
	t.Setenv("AIW_WORKER_ATTEMPT_TIMEOUT_MS", "5000")
	executeTask(context.Background(), 1, client, ClaimedTask{
		TaskID: "task-stop", TaskType: "EXTERNAL_WORK", SkillRef: "coding-work-package-executor", AttemptID: "attempt-stop",
		AttemptNo: 1, LeaseToken: "test-lease", Instruction: "cancel",
	})
	if starts.Load() != 1 || heartbeats.Load() == 0 || acknowledgements.Load() != 1 {
		t.Fatalf("stop request was not acknowledged: starts=%d heartbeats=%d acknowledgements=%d", starts.Load(), heartbeats.Load(), acknowledgements.Load())
	}
	if completions.Load() != 0 {
		t.Fatalf("stop-requested attempt was completed: %d", completions.Load())
	}
}

func TestExecuteTaskLeaseLossDoesNotReportSuccess(t *testing.T) {
	fake := buildFakeCodex(t)
	appServer := NewCodexAppServerRuntime(Config{
		CodexPath: fake, AppServerVersion: codexAppServerVersion,
		ApprovalPolicy: "never", Sandbox: "workspace-write", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelStart()
	if err := appServer.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := appServer.Close(ctx); err != nil {
			t.Errorf("close fake App Server after lease loss: %v", err)
		}
	}()

	var starts, heartbeats, completions, failures atomic.Int32
	controlPlane := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		switch {
		case strings.HasSuffix(request.URL.Path, "/start"):
			starts.Add(1)
			response.WriteHeader(http.StatusNoContent)
		case strings.HasSuffix(request.URL.Path, "/heartbeat"):
			heartbeats.Add(1)
			response.WriteHeader(http.StatusConflict)
			_, _ = io.WriteString(response, `{"error":"lease lost"}`)
		case strings.HasSuffix(request.URL.Path, "/complete"):
			completions.Add(1)
			response.WriteHeader(http.StatusNoContent)
		case strings.HasSuffix(request.URL.Path, "/fail"):
			failures.Add(1)
			response.WriteHeader(http.StatusNoContent)
		default:
			response.WriteHeader(http.StatusNoContent)
		}
	}))
	defer controlPlane.Close()

	workspace := t.TempDir()
	evidenceRoot := t.TempDir()
	t.Setenv("AIW_WORKER_EVIDENCE_ROOT", evidenceRoot)
	t.Setenv("AIW_WORKER_ATTEMPT_TIMEOUT_MS", "5000")
	profilePath := filepath.Join("profiles", "coding-executor.toml")
	client := &Client{
		cfg: Config{
			AgentRuntime: "codex-app-server", AppServerVersion: codexAppServerVersion,
			Profile: loadRoleProfile(profilePath), ProfilePath: profilePath,
			SkillsRoot: "skills", BaseURL: controlPlane.URL, WorkerID: "fd014-test-worker",
			FallbackWorkspace: workspace, HTTPTimeout: time.Second,
			HeartbeatInterval: 20 * time.Millisecond, CodexPath: fake,
		},
		http: &http.Client{Timeout: time.Second}, runtime: appServer,
		forceCtx: context.Background(),
	}
	executeTask(context.Background(), 1, client, ClaimedTask{
		TaskID: "task-lease-loss", TaskType: "EXTERNAL_WORK", SkillRef: "coding-work-package-executor", AttemptID: "attempt-lease-loss",
		AttemptNo: 1, LeaseToken: "test-lease", Instruction: "cancel",
	})

	if starts.Load() != 1 || heartbeats.Load() == 0 {
		t.Fatalf("expected started attempt and lease heartbeat, got starts=%d heartbeats=%d", starts.Load(), heartbeats.Load())
	}
	if completions.Load() != 0 || failures.Load() != 0 {
		t.Fatalf("lease-lost attempt was reported: complete=%d fail=%d", completions.Load(), failures.Load())
	}
	if _, err := os.Stat(workspace); err != nil {
		t.Fatalf("uncertain workspace was released after lease loss: %v", err)
	}
	evidenceDirs, err := os.ReadDir(evidenceRoot)
	if err != nil || len(evidenceDirs) != 1 {
		t.Fatalf("attempt evidence entries = %d, err=%v", len(evidenceDirs), err)
	}
	events, err := os.ReadFile(filepath.Join(evidenceRoot, evidenceDirs[0].Name(), "events.jsonl"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(events), `"event":"resource-terminal"`) || !strings.Contains(string(events), `"status":"unknown"`) {
		t.Fatalf("uncertain lease-lost resource disposition missing from evidence: %s", events)
	}
}

func TestCodexAppServerRealTurn(t *testing.T) {
	if os.Getenv("AIW_RUN_CODEX_APP_SERVER_REAL_TURN") != "1" {
		t.Skip("set AIW_RUN_CODEX_APP_SERVER_REAL_TURN=1 to run the explicitly authorized real turn")
	}
	codexJS := os.Getenv("AIW_CODEX_JS_PATH")
	if codexJS == "" {
		t.Fatal("AIW_CODEX_JS_PATH must point to the installed Codex CLI entrypoint")
	}
	node, err := exec.LookPath("node")
	if err != nil {
		t.Fatal("Node.js is required to launch the installed Codex CLI: ", err)
	}
	launcher := buildCodexNodeLauncher(t, node, codexJS)
	runtime := NewCodexAppServerRuntime(Config{
		CodexPath: launcher, AppServerVersion: codexAppServerVersion,
		ApprovalPolicy: "never", Sandbox: "read-only", ReasoningEffort: "low",
	})
	startCtx, cancelStart := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancelStart()
	if err := runtime.Start(startCtx); err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 50*time.Second)
		defer cancel()
		if err := runtime.Close(ctx); err != nil {
			t.Errorf("close real App Server: %v", err)
		}
	}()

	turnCtx, cancelTurn := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancelTurn()
	result, err := runtime.Run(turnCtx, AgentRunRequest{
		Workspace: t.TempDir(),
		Instruction: "For a protocol smoke check, reply with the exact text: FD-014 APP SERVER TURN OK. Do not use tools.",
	})
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(result.Output) == "" || !strings.Contains(result.Output, "FD-014 APP SERVER TURN OK") {
		t.Fatalf("real turn returned unexpected output: %q", result.Output)
	}
}

func buildFakeCodex(t *testing.T) string {
	t.Helper()
	_, currentFile, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("locate fake App Server fixture")
	}
	fixture := filepath.Join(filepath.Dir(currentFile), "testdata", "fake_app_server", "main.go")
	output := filepath.Join(t.TempDir(), "fake-codex.exe")
	command := exec.Command("go", "build", "-o", output, fixture)
	command.Env = append(os.Environ(), "GOTOOLCHAIN=local", "GOPROXY=off", "GOSUMDB=off")
	if data, err := command.CombinedOutput(); err != nil {
		t.Fatalf("compile fake App Server: %v\n%s", err, data)
	}
	return output
}

func buildCodexNodeLauncher(t *testing.T, nodePath, codexJS string) string {
	t.Helper()
	directory := t.TempDir()
	source := "package main\nimport (\"os\"; \"os/exec\")\nfunc main() { args := append([]string{" + strconv.Quote(codexJS) + "}, os.Args[1:]...); cmd := exec.Command(" + strconv.Quote(nodePath) + ", args...); cmd.Stdin=os.Stdin; cmd.Stdout=os.Stdout; cmd.Stderr=os.Stderr; if err:=cmd.Run(); err!=nil { if e,ok := err.(*exec.ExitError); ok { os.Exit(e.ExitCode()) }; os.Exit(1) } }\n"
	sourcePath := filepath.Join(directory, "codex-launcher.go")
	if err := os.WriteFile(sourcePath, []byte(source), 0o600); err != nil {
		t.Fatal(err)
	}
	output := filepath.Join(directory, "codex-launcher.exe")
	command := exec.Command("go", "build", "-o", output, sourcePath)
	command.Env = append(os.Environ(), "GOTOOLCHAIN=local", "GOPROXY=off", "GOSUMDB=off")
	if data, err := command.CombinedOutput(); err != nil {
		t.Fatalf("compile Codex launcher: %v\n%s", err, data)
	}
	return output
}

func TestCodexVersionDiagnostics(t *testing.T) {
	for output, want := range map[string]string{
		"codex-cli 0.163.0\n": "0.163.0",
		"codex-cli 2.0.0-beta.1\n": "2.0.0-beta.1",
		"new output format\n": "unknown",
	} {
		if got := parseCodexVersion(output); got != want {
			t.Fatalf("parseCodexVersion(%q) = %q, want %q", output, got, want)
		}
	}
}
