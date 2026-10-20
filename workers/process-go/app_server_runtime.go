package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Development baseline only; installed CLI versions are not restricted.
const codexAppServerVersion = "0.162.1"

type appServerMessage struct {
	ID     json.RawMessage `json:"id"`
	Method string          `json:"method"`
	Params json.RawMessage `json:"params"`
	Result json.RawMessage `json:"result"`
	Error  *struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

type appServerRPCResult struct {
	result json.RawMessage
	err    error
}

type CodexAppServerRuntime struct {
	cfg Config
	cliVersion string

	closeMu sync.Mutex
	mu      sync.Mutex
	writer  io.WriteCloser
	cmd     *exec.Cmd
	pending map[string]chan appServerRPCResult
	threads map[string]chan appServerMessage
	nextID  atomic.Int64
	fatalDone chan struct{}
	fatalErr  error
	fatalOnce sync.Once
	done      chan struct{}
	started   bool
}

func NewCodexAppServerRuntime(cfg Config) *CodexAppServerRuntime {
	return &CodexAppServerRuntime{
		cfg: cfg, pending: make(map[string]chan appServerRPCResult),
		threads: make(map[string]chan appServerMessage), fatalDone: make(chan struct{}),
		done: make(chan struct{}),
	}
}

func (*CodexAppServerRuntime) Type() string { return "codex-app-server" }

func (runtime *CodexAppServerRuntime) Failure() <-chan struct{} { return runtime.fatalDone }

func (runtime *CodexAppServerRuntime) FailureError() error { return runtime.failure() }

func (*CodexAppServerRuntime) Capabilities() AgentRuntimeCapabilities {
	return AgentRuntimeCapabilities{AttemptCancellation: true, EventStreaming: true, Tools: true}
}

func (runtime *CodexAppServerRuntime) Start(ctx context.Context) error {
	if err := checkCodexAuthentication(ctx, runtime.cfg, ""); err != nil {
		return err
	}

	versionCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	versionCommand := exec.CommandContext(versionCtx, runtime.cfg.CodexPath, "--version")
	versionCommand.Env = codexChildEnvironment(runtime.cfg)
	versionOutput, err := versionCommand.Output()
	if err != nil {
		return fmt.Errorf("check Codex CLI version: %w", err)
	}
	runtime.cliVersion = parseCodexVersion(string(versionOutput))

	// Keep the child context alive for the runtime lifetime. The startup context
	// only bounds version negotiation and initialization; cancelling it here
	// would terminate the managed server as soon as Start returns.
	cmd := exec.CommandContext(context.Background(), runtime.cfg.CodexPath, "app-server", "--listen", "stdio://",
		"--config", fmt.Sprintf("sandbox_workspace_write.network_access=%t", runtime.cfg.NetworkAccess),
		"--config", "sandbox_read_only.network_access=false")
	cmd.Env = codexChildEnvironment(runtime.cfg)
	configureProcessTree(cmd)
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return fmt.Errorf("open Codex App Server stdin: %w", err)
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		_ = stdin.Close()
		return fmt.Errorf("open Codex App Server stdout: %w", err)
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		_ = stdin.Close()
		return fmt.Errorf("open Codex App Server stderr: %w", err)
	}
	if err := cmd.Start(); err != nil {
		_ = stdin.Close()
		return fmt.Errorf("start Codex App Server: %w", err)
	}
	runtime.mu.Lock()
	runtime.cmd, runtime.writer, runtime.started = cmd, stdin, true
	runtime.mu.Unlock()
	stdoutDone := make(chan struct{})
	stderrDone := make(chan struct{})
	go func() { defer close(stderrDone); _, _ = io.Copy(io.Discard, stderr) }()
	go func() { defer close(stdoutDone); runtime.readLoop(stdout) }()
	fmt.Fprintf(os.Stderr, "codex app-server started pid=%d version=%s\n", cmd.Process.Pid, runtime.cliVersion)
	go func() {
		<-stdoutDone
		<-stderrDone
		waitErr := cmd.Wait()
		close(runtime.done)
		if waitErr != nil {
			runtime.fail(fmt.Errorf("Codex App Server exited: %w", waitErr))
		} else {
			runtime.fail(errors.New("Codex App Server exited"))
		}
	}()

	initialize := map[string]any{
		"clientInfo": map[string]string{
			"name": "aiw_process_go", "title": "AIW Go Process Worker", "version": "1.0.0",
		},
		"capabilities": map[string]any{"experimentalApi": false},
	}
	if _, err := runtime.request(ctx, "initialize", initialize); err != nil {
		closeCtx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		_ = runtime.Close(closeCtx)
		return fmt.Errorf("initialize Codex App Server: %w", err)
	}
	if err := runtime.notify("initialized", map[string]any{}); err != nil {
		closeCtx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		_ = runtime.Close(closeCtx)
		return fmt.Errorf("complete Codex App Server initialization: %w", err)
	}
	if err := runtime.checkAccount(ctx); err != nil {
		closeCtx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		_ = runtime.Close(closeCtx)
		return err
	}
	return nil
}

func parseCodexVersion(output string) string {
	fields := strings.Fields(output)
	if len(fields) >= 2 && fields[0] == "codex-cli" {
		return fields[1]
	}
	return "unknown"
}

func (runtime *CodexAppServerRuntime) Close(ctx context.Context) error {
	// Concurrent turns must observe an actual shutdown outcome, not the
	// started flag set by another caller whose shutdown is still in progress.
	runtime.closeMu.Lock()
	defer runtime.closeMu.Unlock()
	runtime.mu.Lock()
	if runtime.cmd == nil {
		runtime.mu.Unlock()
		return nil
	}
	writer := runtime.writer
	runtime.started = false
	runtime.mu.Unlock()
	if writer != nil {
		_ = writer.Close()
	}

	select {
	case <-runtime.done:
		fmt.Fprintln(os.Stderr, "codex app-server stopped gracefully")
		return nil
	case <-ctx.Done():
	}

	runtime.mu.Lock()
	cmd := runtime.cmd
	runtime.mu.Unlock()
	if cmd != nil && cmd.Process != nil {
		cancelDone := make(chan error, 1)
		go func() { cancelDone <- cmd.Cancel() }()
		select {
		case <-runtime.done:
			fmt.Fprintln(os.Stderr, "codex app-server process tree stopped after shutdown escalation")
			return nil
		case err := <-cancelDone:
			if err != nil {
				return fmt.Errorf("terminate Codex App Server process tree: %w", err)
			}
		case <-time.After(6 * time.Second):
			return errors.New("Codex App Server process tree termination is uncertain")
		}
		select {
		case <-runtime.done:
			fmt.Fprintln(os.Stderr, "codex app-server process tree stopped after shutdown escalation")
			return nil
		case <-time.After(2 * time.Second):
			return errors.New("Codex App Server process exit is unconfirmed")
		}
	}
	return errors.New("Codex App Server process exit is unconfirmed")
}

func (runtime *CodexAppServerRuntime) Run(ctx context.Context, request AgentRunRequest) (result AgentRunResult, runErr error) {
	// Once turn/start can have reached the server, any non-terminal failure
	// must stop the shared process before the caller can release resources.
	turnMayBeActive := false
	defer func() {
		if turnMayBeActive && runErr != nil {
			if err := runtime.stopAfterUnconfirmedCancellation(); err != nil {
				runErr = errors.New("PROCESS_TREE_UNCERTAIN")
			}
		}
	}()
	if err := validateCodexAuthHome(runtime.cfg, request.Workspace); err != nil {
		return AgentRunResult{}, err
	}
	if err := runtime.checkAccount(ctx); err != nil {
		return AgentRunResult{}, err
	}
	threadParams := map[string]any{
		"cwd": request.Workspace, "ephemeral": true,
		"approvalPolicy": runtime.cfg.ApprovalPolicy, "sandbox": runtime.cfg.Sandbox,
	}
	if runtime.cfg.Model != "" {
		threadParams["model"] = runtime.cfg.Model
	}
	if request.Selection != nil && request.Selection.Profile.Model != "" {
		threadParams["model"] = request.Selection.Profile.Model
	}
	threadResponse, err := runtime.request(ctx, "thread/start", threadParams)
	if err != nil {
		if aiPreferencesRejected(err) { return AgentRunResult{}, errAIPreferencesRejected }
		return AgentRunResult{}, fmt.Errorf("start ephemeral Codex thread: %w", err)
	}
	var threadStart struct {
		Thread struct {
			ID string `json:"id"`
		} `json:"thread"`
	}
	if err := json.Unmarshal(threadResponse, &threadStart); err != nil || threadStart.Thread.ID == "" {
		return AgentRunResult{}, errors.New("Codex App Server returned an invalid thread/start response")
	}
	threadID := threadStart.Thread.ID
	events := make(chan appServerMessage, 512)
	runtime.mu.Lock()
	if !runtime.started {
		runtime.mu.Unlock()
		return AgentRunResult{}, errors.New("Codex App Server is not running")
	}
	runtime.threads[threadID] = events
	runtime.mu.Unlock()
	defer func() {
		runtime.mu.Lock()
		delete(runtime.threads, threadID)
		runtime.mu.Unlock()
	}()
	if request.OnSession != nil {
		request.OnSession(threadID)
	}

	turnMayBeActive = true
	turnResponse, err := runtime.request(ctx, "turn/start", map[string]any{
		"threadId": threadID,
		"input": []map[string]any{{"type": "text", "text": request.Instruction}},
		"effort": selectedEffort(request.Selection, runtime.cfg.ReasoningEffort),
		"serviceTierForTurn": selectedServiceTier(request.Selection),
	})
	if err != nil {
		if aiPreferencesRejected(err) { turnMayBeActive = false; return AgentRunResult{SessionID: threadID}, errAIPreferencesRejected }
		return AgentRunResult{SessionID: threadID}, fmt.Errorf("start Codex turn: %w", err)
	}
	var turnStart struct {
		Turn struct {
			ID string `json:"id"`
		} `json:"turn"`
	}
	if err := json.Unmarshal(turnResponse, &turnStart); err != nil || turnStart.Turn.ID == "" {
		return AgentRunResult{SessionID: threadID}, errors.New("Codex App Server returned an invalid turn/start response")
	}
	turnID := turnStart.Turn.ID
	var deltas strings.Builder
    workStarted := false
	for {
		select {
		case <-ctx.Done():
			if err := runtime.interrupt(ctx, threadID, turnID); err == nil {
				if err := runtime.waitForTerminal(threadID, turnID, events, &deltas, 5*time.Second); err == nil {
					turnMayBeActive = false
				}
			}
			return AgentRunResult{SessionID: threadID}, ctx.Err()
		case <-runtime.fatalDone:
			return AgentRunResult{SessionID: threadID}, fmt.Errorf("Codex App Server connection failed: %w", runtime.failure())
		case event := <-events:
            if event.Method == "item/started" || event.Method == "item/completed" || event.Method == "item/agentMessage/delta" { workStarted = true }
			if eventErr := runtime.consumeTurnEvent(event, threadID, turnID, &deltas); eventErr != nil {
				if errors.Is(eventErr, errTurnCompleted) || errors.Is(eventErr, errTurnFailed) || errors.Is(eventErr, errTurnInterrupted) {
					turnMayBeActive = false
				}
				if errors.Is(eventErr, errTurnCompleted) {
					return AgentRunResult{SessionID: threadID, Output: turnOutput(event, deltas.String())}, nil
				}
				if errors.Is(eventErr, errTurnFailed) && !workStarted {
                    var failure struct { Turn struct { Error any `json:"error"` } `json:"turn"` }
                    if json.Unmarshal(event.Params, &failure) == nil && aiPreferencesRejected(failure.Turn.Error) { return AgentRunResult{SessionID: threadID}, errAIPreferencesRejected }
                }
                return AgentRunResult{SessionID: threadID, Output: turnOutput(event, deltas.String())}, eventErr
			}
		}
	}
}

var errTurnCompleted = errors.New("turn completed")
var errTurnFailed = errors.New("CODEX_TURN_FAILED")
var errTurnInterrupted = errors.New("CODEX_TURN_INTERRUPTED")

func (runtime *CodexAppServerRuntime) consumeTurnEvent(event appServerMessage, threadID, turnID string, deltas *strings.Builder) error {
	var params struct {
		ThreadID string          `json:"threadId"`
		TurnID   string          `json:"turnId"`
		Delta    string          `json:"delta"`
		Turn     struct {
			ID     string          `json:"id"`
			Status string          `json:"status"`
		} `json:"turn"`
	}
	if err := json.Unmarshal(event.Params, &params); err != nil {
		return fmt.Errorf("decode App Server event %s: %w", event.Method, err)
	}
	if params.ThreadID != threadID {
		return nil
	}
	switch event.Method {
	case "item/agentMessage/delta":
		if params.TurnID == turnID {
			deltas.WriteString(params.Delta)
		}
	case "turn/completed":
		if params.Turn.ID != turnID {
			return nil
		}
		switch params.Turn.Status {
		case "completed":
			return errTurnCompleted
		case "failed":
			return errTurnFailed
		case "interrupted":
			return errTurnInterrupted
		default:
			return fmt.Errorf("unknown Codex turn status %q", params.Turn.Status)
		}
	}
	return nil
}

func turnOutput(event appServerMessage, deltas string) string {
	var params struct {
		Turn struct {
			Items []struct {
				Type string `json:"type"`
				Text string `json:"text"`
				Content []struct {
					Text string `json:"text"`
				} `json:"content"`
			} `json:"items"`
		} `json:"turn"`
	}
	if json.Unmarshal(event.Params, &params) == nil {
		var output strings.Builder
		for _, item := range params.Turn.Items {
			if item.Type != "agentMessage" && item.Type != "agent_message" {
				continue
			}
			if item.Text != "" {
				output.WriteString(item.Text)
			}
			for _, content := range item.Content {
				output.WriteString(content.Text)
			}
		}
		if output.Len() > 0 {
			return output.String()
		}
	}
	return deltas
}

func (runtime *CodexAppServerRuntime) waitForTerminal(threadID, turnID string, events <-chan appServerMessage, deltas *strings.Builder, timeout time.Duration) error {
	timer := time.NewTimer(timeout)
	defer timer.Stop()
	for {
		select {
		case event := <-events:
			if event.Method == "turn/completed" {
				var terminal struct {
					ThreadID string `json:"threadId"`
					Turn     struct {
						ID string `json:"id"`
					} `json:"turn"`
				}
				if err := json.Unmarshal(event.Params, &terminal); err != nil {
					return fmt.Errorf("decode App Server cancellation terminal: %w", err)
				}
				if terminal.ThreadID == threadID && terminal.Turn.ID == turnID {
					return nil
				}
			}
			if err := runtime.consumeTurnEvent(event, threadID, turnID, deltas); err != nil {
				if errors.Is(err, errTurnCompleted) {
					return nil
				}
				return err
			}
		case <-runtime.fatalDone:
			return runtime.failure()
		case <-timer.C:
			return errors.New("timed out waiting for turn/completed after cancellation")
		}
	}
}

func (runtime *CodexAppServerRuntime) interrupt(ctx context.Context, threadID, turnID string) error {
	interruptCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, err := runtime.request(interruptCtx, "turn/interrupt", map[string]string{"threadId": threadID, "turnId": turnID})
	if err != nil {
		return fmt.Errorf("turn/interrupt acknowledgement: %w", err)
	}
	return nil
}

func (runtime *CodexAppServerRuntime) stopAfterUnconfirmedCancellation() error {
	ctx, cancel := context.WithTimeout(context.Background(), 7*time.Second)
	defer cancel()
	return runtime.Close(ctx)
}

func (runtime *CodexAppServerRuntime) request(ctx context.Context, method string, params any) (json.RawMessage, error) {
	id := runtime.nextID.Add(1)
	idKey := strconv.FormatInt(id, 10)
	response := make(chan appServerRPCResult, 1)
	runtime.mu.Lock()
	if !runtime.started && method != "initialize" {
		runtime.mu.Unlock()
		return nil, errors.New("Codex App Server is not running")
	}
	runtime.pending[idKey] = response
	runtime.mu.Unlock()
	message, err := json.Marshal(map[string]any{"jsonrpc": "2.0", "id": id, "method": method, "params": params})
	if err != nil {
		runtime.removePending(idKey)
		return nil, err
	}
	if err := runtime.writeLine(message); err != nil {
		runtime.removePending(idKey)
		return nil, err
	}
	select {
	case result := <-response:
		return result.result, result.err
	case <-runtime.fatalDone:
		runtime.removePending(idKey)
		return nil, runtime.failure()
	case <-ctx.Done():
		runtime.removePending(idKey)
		return nil, ctx.Err()
	}
}

func (runtime *CodexAppServerRuntime) notify(method string, params any) error {
	message, err := json.Marshal(map[string]any{"jsonrpc": "2.0", "method": method, "params": params})
	if err != nil {
		return err
	}
	return runtime.writeLine(message)
}

func (runtime *CodexAppServerRuntime) writeLine(message []byte) error {
	runtime.mu.Lock()
	writer := runtime.writer
	runtime.mu.Unlock()
	if writer == nil {
		return errors.New("Codex App Server stdin is unavailable")
	}
	line := append(bytes.Clone(message), '\n')
	runtime.mu.Lock()
	defer runtime.mu.Unlock()
	_, err := writer.Write(line)
	return err
}

func (runtime *CodexAppServerRuntime) removePending(id string) {
	runtime.mu.Lock()
	delete(runtime.pending, id)
	runtime.mu.Unlock()
}

func (runtime *CodexAppServerRuntime) readLoop(stdout io.Reader) {
	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for scanner.Scan() {
		var message appServerMessage
		if err := json.Unmarshal(scanner.Bytes(), &message); err != nil {
			runtime.fail(fmt.Errorf("decode Codex App Server message: %w", err))
			continue
		}
		if len(message.ID) > 0 && string(message.ID) != "null" && message.Method != "" {
			_ = runtime.writeLine([]byte(fmt.Sprintf(`{"jsonrpc":"2.0","id":%s,"error":{"code":-32601,"message":"client-initiated server requests are unsupported"}}`, message.ID)))
			continue
		}
		if len(message.ID) > 0 && string(message.ID) != "null" {
			runtime.handleResponse(message)
			continue
		}
		if message.Method != "" {
			runtime.handleNotification(message)
		}
	}
	if err := scanner.Err(); err != nil {
		runtime.fail(fmt.Errorf("read Codex App Server output: %w", err))
	} else {
		runtime.fail(io.EOF)
	}
}

func (runtime *CodexAppServerRuntime) handleResponse(message appServerMessage) {
	key := string(message.ID)
	runtime.mu.Lock()
	response := runtime.pending[key]
	delete(runtime.pending, key)
	runtime.mu.Unlock()
	if response == nil {
		return
	}
	if message.Error != nil {
		response <- appServerRPCResult{err: fmt.Errorf("App Server RPC %d: %s", message.Error.Code, message.Error.Message)}
		return
	}
	response <- appServerRPCResult{result: message.Result}
}

func (runtime *CodexAppServerRuntime) handleNotification(message appServerMessage) {
	var route struct {
		ThreadID string `json:"threadId"`
	}
	if err := json.Unmarshal(message.Params, &route); err != nil || route.ThreadID == "" {
		return
	}
	runtime.mu.Lock()
	events := runtime.threads[route.ThreadID]
	runtime.mu.Unlock()
	if events == nil {
		return
	}
	select {
	case events <- message:
	default:
		runtime.fail(fmt.Errorf("App Server event backlog exceeded for thread %s", route.ThreadID))
	}
}

func (runtime *CodexAppServerRuntime) fail(err error) {
	runtime.fatalOnce.Do(func() {
		fmt.Fprintln(os.Stderr, "codex app-server transport failed:", err)
		runtime.mu.Lock()
		runtime.fatalErr = err
		runtime.mu.Unlock()
		close(runtime.fatalDone)
	})
}

func (runtime *CodexAppServerRuntime) failure() error {
	runtime.mu.Lock()
	defer runtime.mu.Unlock()
	if runtime.fatalErr == nil {
		return io.EOF
	}
	return runtime.fatalErr
}

func selectedEffort(selection *NamedAIProfile, fallback string) string {
	if selection != nil && selection.Profile.Effort != "" {
		return selection.Profile.Effort
	}
	return fallback
}

func selectedServiceTier(selection *NamedAIProfile) string {
	if selection != nil && selection.Profile.Fast {
		return "fast"
	}
	return "default"
}
