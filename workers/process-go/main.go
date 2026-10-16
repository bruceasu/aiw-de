package main

import (
	"bufio"
	"bytes"
	"context"
    "crypto/sha256"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/BurntSushi/toml"
)

type RoleProfile struct {
	Name             string   `toml:"name"`
	WorkerIDPrefix   string   `toml:"workerIdPrefix"`
	RoleCapability   string   `toml:"roleCapability"`
	AllowedTaskTypes []string `toml:"allowedTaskTypes"`
	ProtocolSkills   []string `toml:"protocolSkills"`
	PrimarySkill     string   `toml:"primarySkill"`
	Instruction      string   `toml:"instruction"`
	ReadOnly         bool     `toml:"readOnly"`
	ResultKind       string   `toml:"resultKind"`
}

type Config struct {
	AgentRuntime      string
	AppServerVersion  string
	CodexAuthMode     string
	CodexAuthHome     string
	Profile           RoleProfile
	ProfilePath       string
	SkillsRoot        string
	IncludeReferences bool
	BaseURL           string
	WorkerID          string
	Token             string
	Capabilities      []string
	MaxConcurrency    int
	FallbackWorkspace string
	RepositoryRef     string
	RepositoryRoot    string
	WorktreeRoot      string
	PollInterval      time.Duration
	HeartbeatInterval time.Duration
	HTTPTimeout       time.Duration
	CodexPath         string
	Model             string
	Sandbox           string
	ApprovalPolicy    string
	ReasoningEffort   string
	Fast              bool
	AIProfileSelected bool
	NetworkAccess     bool
	OpenAIAPIKey      string
	OpenAIBaseURL     string
	OpenAIModel       string
	OpenAIReasoningEffort string
	OpenAITimeout     time.Duration
	AgentMaxTurns     int
	AgentMaxOutputBytes int
	AgentToolTimeout time.Duration
	AgentAllowShell   bool
}

type WorkspaceAssignment struct {
	RepositoryRef string `json:"repositoryRef"`
	RepositoryPath string `json:"repositoryPath"`
	BaseRevision  string `json:"baseRevision"`
	WorkspaceKey  string `json:"workspaceKey"`
	BranchName    string `json:"branchName"`
}

type WorkspaceResult struct {
	ResultRevision string   `json:"resultRevision"`
	ChangedFiles   []string `json:"changedFiles"`
	PublicationRef string   `json:"publicationRef,omitempty"`
}

type TaskInput struct {
	TaskID            string   `json:"taskId"`
	TaskType          string   `json:"taskType"`
	RepositoryRef     string   `json:"repositoryRef"`
	BaseRevision      string   `json:"baseRevision"`
	ResultRevision    string   `json:"resultRevision"`
	PublicationRef    string   `json:"publicationRef"`
	ChangedFiles      []string `json:"changedFiles"`
	CompletionVerdict string   `json:"completionVerdict"`
	CompletionSummary string   `json:"completionSummary"`
}

type CompletionReport struct {
	Verdict string `json:"verdict"`
	Summary string `json:"summary"`
}

type ClaimedTask struct {
    AIPreferences *AIPreferences `json:"aiPreferences"`
	TaskID      string               `json:"taskId"`
	TaskType    string               `json:"taskType"`
	SubjectType string               `json:"subjectType"`
	SubjectID   string               `json:"subjectId"`
	Instruction string               `json:"instruction"`
	SkillRef    string               `json:"skillRef"`
	AttemptID   string               `json:"attemptId"`
	AttemptNo   int                  `json:"attemptNo"`
	LeaseToken  string               `json:"leaseToken"`
	Workspace   *WorkspaceAssignment `json:"workspace"`
	Inputs      []TaskInput          `json:"inputs"`
	AIGuidance  *AIGuidance          `json:"aiGuidance"`
}

type AIProfile struct {
	Provider string `json:"provider"`
	Runtime  string `json:"runtime"`
	Model    string `json:"model"`
	Effort   string `json:"effort"`
	Fast     bool   `json:"fast"`
}
type NamedAIProfile struct {
	ID      string    `json:"id"`
	Profile AIProfile `json:"profile"`
}
type AIHistory struct {
	ProfileID       string `json:"profileId"`
	FailureCategory string `json:"failureCategory"`
	State           string `json:"state"`
}
type AIGuidance struct {
	Version          string           `json:"version"`
	AllowedProfiles  []NamedAIProfile `json:"allowedProfiles"`
	Previous         *AIHistory       `json:"previous"`
	SuggestedProfile string           `json:"suggestedProfile"`
}

func chooseAIProfile(g *AIGuidance, runtime string) (*NamedAIProfile, string, error) {
	if g == nil { return nil, "", nil }
	allowed := make([]NamedAIProfile, 0, len(g.AllowedProfiles))
	for _, item := range g.AllowedProfiles {
		if item.Profile.Provider == "openai" && item.Profile.Runtime == runtime && (runtime == "codex-cli" || !item.Profile.Fast) { allowed = append(allowed, item) }
	}
	if len(allowed) == 0 { return nil, "", fmt.Errorf("no approved %s profile", runtime) }
	index := -1
	if g.Previous != nil {
		for i, item := range allowed { if item.ID == g.Previous.ProfileID { index = i; break } }
	}
	if index < 0 { return &allowed[0], "initial approved profile", nil }
	if g.Previous.FailureCategory == "MODEL_ERROR" || g.Previous.FailureCategory == "OUTPUT_INVALID" {
		if index + 1 < len(allowed) { index++ }
		return &allowed[index], "model failure; select next approved profile", nil
	}
	return &allowed[index], "retain profile after non-model failure", nil
}

type GitWorkspace struct {
    ctx context.Context
    recorder *AttemptEvidence
    owned bool
	cfg        Config
	cwd        string
	repositoryRoot string
	assignment *WorkspaceAssignment
}

type Client struct {
    forceCtx context.Context
	cfg  Config
	http *http.Client
	runtime AgentRuntime
}

type protocolError struct {
	status int
	body   string
}

func (e protocolError) Error() string {
	return fmt.Sprintf("worker protocol HTTP %d: %s", e.status, e.body)
}

func main() {
	cfg := loadConfig()
	runtime, err := resolveAgentRuntime(cfg)
	must(err)
	client := &Client{cfg: cfg, http: &http.Client{Timeout: cfg.HTTPTimeout}, runtime: runtime}
	root, cancel := context.WithCancel(context.Background())
    defer cancel()
    force, forceCancel := context.WithCancel(context.Background())
    defer forceCancel()
    client.forceCtx = force
	if managed, ok := runtime.(managedAgentRuntime); ok {
		startupCtx, startupCancel := context.WithTimeout(root, cfg.HTTPTimeout)
		err := managed.Start(startupCtx)
		startupCancel()
		must(err)
		defer func() {
			shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 50*time.Second)
			defer shutdownCancel()
			if err := managed.Close(shutdownCtx); err != nil {
				fmt.Fprintln(os.Stderr, "app-server shutdown:", err)
			}
		}()
	}
    signals := make(chan os.Signal, 2)
    signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
    defer signal.Stop(signals)
    go func() {
        select { case <-signals: case <-force.Done(): return }
        cancel()
        drainCtx, done := context.WithTimeout(context.Background(), cfg.HTTPTimeout)
        _ = client.post(drainCtx, "/workers/"+url.PathEscape(cfg.WorkerID)+"/drain", nil, nil); done()
        select { case <-signals: forceCancel(); case <-force.Done(): }
    }()

	must(client.register(root))
	fmt.Printf("registered %s (%d slots)\n", cfg.WorkerID, cfg.MaxConcurrency)
	if monitored, ok := runtime.(failureAwareRuntime); ok {
		go func() {
			select {
			case <-root.Done():
			case <-monitored.Failure():
				fmt.Fprintln(os.Stderr, "agent runtime failed; stopping new claims:", monitored.FailureError())
				cancel()
			}
		}()
	}

	hbCtx, hbCancel := context.WithCancel(root)
	defer hbCancel()
	go workerHeartbeatLoop(hbCtx, client)

	var wg sync.WaitGroup
	for i := 1; i <= cfg.MaxConcurrency; i++ {
		wg.Add(1)
		go func(slot int) { defer wg.Done(); slotLoop(root, slot, client) }(i)
	}
	wg.Wait()

	shutdownCtx, stop := context.WithTimeout(context.Background(), 3*time.Second)
	defer stop()
	_ = client.post(shutdownCtx, "/workers/"+url.PathEscape(cfg.WorkerID)+"/drain", nil, nil)
	_ = client.post(shutdownCtx, "/workers/"+url.PathEscape(cfg.WorkerID)+"/offline", nil, nil)
}

func workerHeartbeatLoop(ctx context.Context, client *Client) {
	ticker := time.NewTicker(client.cfg.HeartbeatInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := client.post(ctx, "/workers/"+url.PathEscape(client.cfg.WorkerID)+"/heartbeat", nil, nil); err != nil {
				fmt.Fprintln(os.Stderr, "worker heartbeat:", err)
			}
		}
	}
}

func slotLoop(ctx context.Context, slot int, client *Client) {
	for ctx.Err() == nil {
		task, err := client.claim(ctx)
		if err != nil {
			fmt.Fprintf(os.Stderr, "slot %d claim: %v\n", slot, err)
			sleepContext(ctx, client.cfg.PollInterval)
			continue
		}
		if task == nil {
			sleepContext(ctx, client.cfg.PollInterval)
			continue
		}
		if !executeTask(client.forceCtx, slot, client, *task) {
			fmt.Fprintf(os.Stderr, "slot %d quarantined: resource termination is unknown\n", slot)
			return
		}
	}
}

func executeTask(parent context.Context, slot int, client *Client, task ClaimedTask) bool {
    if parent == nil { parent = context.Background() }
    timeout := time.Duration(positiveInt("AIW_WORKER_ATTEMPT_TIMEOUT_MS", positiveInt("AIW_AGENT_TASK_TIMEOUT_MS", 900000)))*time.Millisecond
    runCtx, cancel := context.WithTimeout(parent, timeout); defer cancel()
	evidence, err := newAttemptEvidence(client.cfg, task)
    if err != nil { fmt.Fprintf(os.Stderr, "attempt %s: CRITICAL_EVIDENCE_FAILED\n", task.AttemptID); return true }
	cfg := client.cfg
	processTree := "native-codex-cli"
	if client.runtime.Type() == "openai-api" { processTree = "direct-http" }
	if client.runtime.Type() == "codex-app-server" { processTree = "managed-codex-app-server" }
	caps := client.runtime.Capabilities()
	capability := fmt.Sprintf("%s;resume=%t;attemptCancellation=%t;eventStreaming=%t;tools=%t", client.runtime.Type(), caps.Resume, caps.AttemptCancellation, caps.EventStreaming, caps.Tools)
	runtimeEvidence := map[string]any{"capability": capability, "processTree": processTree, "unknown": "runtime-specific-tool-policy-and-model-window"}
	if appServer, ok := client.runtime.(*CodexAppServerRuntime); ok { runtimeEvidence["cliVersion"] = appServer.cliVersion; runtimeEvidence["protocol"] = "app-server-v2"; runtimeEvidence["threadPersistence"] = "ephemeral"; runtimeEvidence["usage"] = "unknown" }
	if err = evidence.record("runtime-capabilities", runtimeEvidence); err != nil { fmt.Fprintf(os.Stderr, "attempt %s: CRITICAL_EVIDENCE_FAILED\n", task.AttemptID); return true }
    var workspace *GitWorkspace
    var mu sync.RWMutex
    sessionID := ""
    lost, stopRequested, publicationStarted, reporting, started, finished, cleaned, uncertain := false, false, false, false, false, false, false, false
    runtimeStopped := false
    var releaseWorkspace func()
    defer func() { if releaseWorkspace != nil && !uncertain { releaseWorkspace() } }()
    phase := "TOOL_ERROR"
    stopHeartbeat := make(chan struct{})
    heartbeatDone := make(chan struct{})
    heartbeatStarted := false
    resultErr := func() error {
		profileRuntime := cfg.AgentRuntime
		selection, reason, e := chooseAIProfile(task.AIGuidance, profileRuntime); if e != nil { return e }
        fallbackSelection := selection
        effectiveSelection := stepAISelection(task, cfg, selection)
        if task.AIGuidance != nil && effectiveSelection != selection { selection = effectiveSelection; reason = "Step preference matches an approved profile" }
        if e = evidence.record("ai-settings", map[string]any{"reason": aiPreferenceReason(task, cfg, effectiveSelection), "model": selectedModel(effectiveSelection, cfg), "effort": selectedEffort(effectiveSelection, workerEffort(cfg))}); e != nil { return e }
        if e = client.start(runCtx, task, selection, reason); e != nil {
            var response struct { StopRequested bool `json:"stopRequested"` }
            if client.heartbeat(runCtx, task, "", &response) == nil && response.StopRequested { stopRequested = true }
            return e
        }; started = true
        if e = evidence.record("attempt-running", map[string]any{"status": "running"}); e != nil { return e }
        heartbeatStarted = true
        go func() {
            defer close(heartbeatDone)
            ticker := time.NewTicker(cfg.HeartbeatInterval); defer ticker.Stop()
            for { select {
            case <-stopHeartbeat: return
            case <-runCtx.Done(): return
            case <-ticker.C:
                mu.RLock(); sid := sessionID; mu.RUnlock()
                var response struct { StopRequested bool `json:"stopRequested"` }
                if e := client.heartbeat(runCtx, task, sid, &response); e != nil {
                    mu.Lock(); lost = true; mu.Unlock(); cancel()
                    _ = evidence.record("lease-lost", map[string]any{"status": "unknown"}); return
                }
                if response.StopRequested { mu.Lock(); stopRequested = true; mu.Unlock(); cancel(); return }
            } }
        }()
        if !taskTypeAllowed(cfg.Profile, task.TaskType) { return errors.New("ROLE_TASK_TYPE_DENIED") }
        if task.TaskType != "EXTERNAL_WORK" || task.SkillRef != cfg.Profile.PrimarySkill { return errors.New("ROLE_SKILL_DENIED") }
        if cfg.Profile.ResultKind == "WORKSPACE" && (task.Workspace == nil || !validResultRevision(task.Workspace.BaseRevision)) { return errors.New("WRITABLE_WORKSPACE_REQUIRED") }
        if cfg.Profile.ResultKind == "VERDICT" { for _, input := range task.Inputs {
            if input.ResultRevision != "" && (task.Workspace == nil || input.ResultRevision != task.Workspace.BaseRevision) { return errors.New("UPSTREAM_REVISION_MISMATCH") }
        } }
        // Freeze trusted skill input before preparing a workspace.
        frozenSkills, e := freezeGoSkills(runCtx, cfg, evidence); if e != nil { return e }

        var e2 error
        workspace, e2 = materializeWorkspace(runCtx, cfg, task, evidence); if e2 != nil { return e2 }
        release, e := lockWorkspace(runCtx, workspace.cwd); if e != nil { return e }; releaseWorkspace = release
        baseline := ""
        if cfg.Profile.ReadOnly { baseline, e = workspaceDigest(runCtx, workspace.cwd); if e != nil { return e } }
        instruction, e := assembleGoContextWithSkills(runCtx, cfg, task, evidence, frozenSkills); if e != nil { return e }
		phase = "MODEL_ERROR"
		runRequest := AgentRunRequest{Task: task, Workspace: workspace.cwd, ReadOnly: cfg.Profile.ReadOnly,
			Instruction: instruction, Selection: effectiveSelection, Evidence: evidence,
			OnSession: func(id string) { mu.Lock(); sessionID = id; mu.Unlock() }}
        runResult, e := client.runtime.Run(runCtx, runRequest)
        if errors.Is(e, errAIPreferencesRejected) && task.AIPreferences != nil && runCtx.Err() == nil && !sameAISettings(effectiveSelection, fallbackSelection, cfg) {
            if e = evidence.record("ai-settings-fallback", map[string]any{"reason":"Step model or effort rejected before tool execution; use Worker choice", "model": selectedModel(fallbackSelection, cfg), "effort": selectedEffort(fallbackSelection, workerEffort(cfg))}); e != nil { return e }
            runRequest.Selection = fallbackSelection
            runResult, e = client.runtime.Run(runCtx, runRequest)
        }
		// App Server guarantees terminal confirmation or a successful shared
		// shutdown on every return except PROCESS_TREE_UNCERTAIN.
		runtimeStopped = client.runtime.Type() == "codex-app-server" && (e == nil || e.Error() != "PROCESS_TREE_UNCERTAIN")
		if e != nil { mu.RLock(); stopping := stopRequested; mu.RUnlock(); if !stopping && !runtimeStopped { uncertain = true }; return e }
		mu.Lock(); if runResult.SessionID != "" { sessionID = runResult.SessionID }; leaseLost := lost; mu.Unlock()
        if leaseLost || runCtx.Err() != nil { return errors.New("ATTEMPT_CANCELLED") }
        phase = "OUTPUT_INVALID"
		report, e := completionReport(cfg.Profile.ResultKind, runResult.Output); if e != nil { return errors.New("COMPLETION_REPORT_INVALID") }
        if cfg.Profile.ReadOnly { current, e := workspaceDigest(runCtx, workspace.cwd); if e != nil { return e }; if current != baseline { return errors.New("READ_ONLY_WORKSPACE_CHANGED") } }
        if cfg.Profile.ReadOnly && workspace.assignment != nil && workspace.assignment.BaseRevision != "" { head, e := gitTextContext(runCtx, workspace.cwd, "rev-parse", "HEAD"); if e != nil { return e }; if strings.TrimSpace(head) != workspace.assignment.BaseRevision { return errors.New("READ_ONLY_REVISION_CHANGED") } }
		if e = evidence.record("report-validated", map[string]any{"status": "valid", "digest": fmt.Sprintf("%x", sha256.Sum256([]byte(runResult.Output)))}); e != nil { return e }
        phase = "TOOL_ERROR"
        publicationStarted = true
        result, e := workspace.finalize(task); if e != nil { return e }
        if cfg.Profile.ResultKind == "WORKSPACE" && (result == nil || !validResultRevision(result.ResultRevision)) { return errors.New("WORKSPACE_RESULT_INVALID") }
        close(stopHeartbeat); <-heartbeatDone; heartbeatStarted = false
        mu.RLock(); leaseLost = lost; mu.RUnlock()
        if leaseLost || runCtx.Err() != nil { return errors.New("ATTEMPT_CANCELLED") }
        if e = evidence.record("report-posting", map[string]any{"reportStatus": "pending"}); e != nil { return e }
        if e = workspace.cleanup(); e != nil { return e }; cleaned = true
        if e = evidence.finish("ready-to-report"); e != nil { return e }; finished = true
        if runCtx.Err() != nil { return runCtx.Err() }
        reporting = true
        if e = client.complete(runCtx, task, sessionID, result, report); e != nil { return errors.New("COMPLETE_RESPONSE_UNKNOWN") }
        return evidence.record("report-posted", map[string]any{"reportStatus": "accepted"})
    }()
    if heartbeatStarted { close(stopHeartbeat); <-heartbeatDone }
    mu.RLock(); leaseLost := lost; sid := sessionID; mu.RUnlock()
    if resultErr != nil {
        if resultErr.Error() == "PROCESS_TREE_UNCERTAIN" { uncertain = true }
        mu.RLock(); stopping := stopRequested; mu.RUnlock()
        if runCtx.Err() != nil && !stopping && !runtimeStopped { uncertain = true }
        status := "failed"; if leaseLost { status = "lease-lost" }; if reporting { status = "report-unknown" }
        _ = evidence.record("attempt-failed", map[string]any{"status": status, "phase": phase, "code": "OPERATION_FAILED"})
        if runCtx.Err() != nil || leaseLost { reason := "force-shutdown"; if runCtx.Err() == context.DeadlineExceeded { reason = "deadline" }; if leaseLost { reason = "lease-lost" }; _ = evidence.record("attempt-cancelled", map[string]any{"reason": reason}) }
        if started && !stopping && !leaseLost && !reporting && !uncertain { reportCtx, stop := context.WithTimeout(context.Background(), cfg.HTTPTimeout); _ = client.fail(reportCtx, task, sid, "OPERATION_FAILED", phase); stop() }
        fmt.Fprintf(os.Stderr, "slot %d attempt %s: %s\n", slot, task.AttemptID, status)
    } else { fmt.Printf("slot %d attempt %s: completed\n", slot, task.AttemptID) }
    if workspace != nil && !cleaned && !uncertain { _ = workspace.cleanup() }
    if uncertain { _ = evidence.record("resource-terminal", map[string]any{"status": "unknown", "code": "WORKSPACE_PRESERVED"}) } else if !finished { _ = evidence.finish("failed") }
    mu.RLock(); stopping := stopRequested; mu.RUnlock()
    if stopping && !publicationStarted && !uncertain && !reporting {
        reportCtx, release := context.WithTimeout(context.Background(), cfg.HTTPTimeout)
        if err := client.action(reportCtx, task, "stop-acknowledgement", ""); err != nil { fmt.Fprintln(os.Stderr, "stop acknowledgement:", err) }
        release()
    }
    return !uncertain
}
func runCodex(ctx context.Context, cfg Config, workspace string, instruction string, onSession func(string), evidences ...*AttemptEvidence) (string, string, error) {
    var evidence *AttemptEvidence
    if len(evidences) > 0 { evidence = evidences[0] }
    completed := false
    failedTool := false
	args := []string{"exec", "--ephemeral", "--json", "--sandbox", cfg.Sandbox, "--cd", workspace,
		"--config", fmt.Sprintf("approval_policy=\"%s\"", cfg.ApprovalPolicy),
		"--config", fmt.Sprintf("model_reasoning_effort=\"%s\"", cfg.ReasoningEffort),
		"--config", fmt.Sprintf("sandbox_workspace_write.network_access=%t", cfg.NetworkAccess),
	}
	if cfg.AIProfileSelected {
		tier := "default"
		if cfg.Fast { tier = "fast" }
		args = append(args, "--config", fmt.Sprintf("service_tier=\"%s\"", tier))
	}
	if cfg.Model != "" {
		args = append(args, "--model", cfg.Model)
	}
	cmd := exec.CommandContext(ctx, cfg.CodexPath, args...)
	cmd.Dir = workspace
    cmd.Stdin = strings.NewReader(instruction)
	cmd.Env = codexChildEnvironment(cfg)
    configureProcessTree(cmd)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return "", "", err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return "", "", err
	}
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	stderrClassification := &aiPreferenceCapture{}
    stderrDone := make(chan struct{})
    go func() { defer close(stderrDone); _, _ = io.Copy(stderrClassification, stderr) }()

	sessionID := ""
	finalMessage := ""
	terminalFailed := ""
    preferenceRejected, workStarted := false, false

	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for scanner.Scan() {
		line := scanner.Bytes()
		var event map[string]any
		if err := json.Unmarshal(line, &event); err != nil {
			terminalFailed = "CODEX_JSON_INVALID"
            _ = cmd.Cancel()
            break
		}
		typ, _ := event["type"].(string)
        if typ == "item.started" || typ == "item.completed" { if item, ok := event["item"].(map[string]any); ok && item["type"] != "reasoning" { workStarted = true } }
		switch typ {
		case "thread.started":
			if id, ok := event["thread_id"].(string); ok {
				sessionID = id
				onSession(id)
			}
		case "item.completed":
			if item, ok := event["item"].(map[string]any); ok {
				if itemType, _ := item["type"].(string); itemType == "agent_message" {
					finalMessage, _ = item["text"].(string)
                } else {
                    status, _ := item["status"].(string)
                    if status == "failed" { failedTool = true }
                    if item["type"] == "command_execution" { if exit, ok := item["exit_code"].(float64); ok && exit != 0 { failedTool = true } }
                    if evidence != nil { if e := evidence.record("sdk-item-terminal", map[string]any{"callId": item["id"], "tool": item["type"], "status": status, "unknown": "inner-permission-and-resource-locks"}); e != nil { terminalFailed = "CRITICAL_EVIDENCE_FAILED"; _ = cmd.Cancel() } }
                }
			}
		case "turn.completed":
            completed = true
            if evidence != nil {
                values := map[string]any{"status": "completed", "usageKnown": false}
                if usage, ok := event["usage"].(map[string]any); ok {
                    input, i := usage["input_tokens"].(float64); output, o := usage["output_tokens"].(float64)
                    if i && o { values["usageKnown"] = true; values["inputTokens"], values["outputTokens"] = input, output }
                }
                if e := evidence.record("model-terminal", values); e != nil { terminalFailed = "CRITICAL_EVIDENCE_FAILED"; _ = cmd.Cancel() }
            }
        case "turn.failed", "error":
            terminalFailed = "CODEX_TURN_FAILED"
            preferenceRejected = preferenceRejected || aiPreferencesRejected(event["error"]) || aiPreferencesRejected(event["message"])
		}
	}
	scanErr := scanner.Err()
	waitErr := cmd.Wait()
    <-stderrDone
	if ctx.Err() != nil {
		return sessionID, finalMessage, ctx.Err()
	}
	if scanErr != nil {
		return sessionID, finalMessage, scanErr
	}
	if !workStarted && (preferenceRejected || stderrClassification.rejected.Load()) { return sessionID, finalMessage, errAIPreferencesRejected }
	if terminalFailed != "" {
		return sessionID, finalMessage, errors.New(terminalFailed)
	}
	if waitErr != nil {
		return sessionID, finalMessage, waitErr
	}
	if !completed || strings.TrimSpace(finalMessage) == "" || failedTool { return sessionID, finalMessage, errors.New("CODEX_TERMINAL_OR_TOOL_INVALID") }
    return sessionID, finalMessage, nil
}

func (c *Client) register(ctx context.Context) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "runtimeType": c.runtime.Type() + ":" + c.cfg.Profile.Name, "capabilities": c.cfg.Capabilities, "maxConcurrency": c.cfg.MaxConcurrency}
	return c.post(ctx, "/workers/register", payload, nil)
}

func completionReport(resultKind string, finalMessage string) (*CompletionReport, error) {
    summary := strings.TrimSpace(finalMessage)
    if summary == "" { return nil, errors.New("COMPLETION_REPORT_EMPTY") }
    if resultKind == "REPORT" || resultKind == "WORKSPACE" { return &CompletionReport{Verdict: "PASS", Summary: summary}, nil }
    if resultKind != "VERDICT" { return nil, errors.New("RESULT_KIND_INVALID") }
    lines := strings.Split(summary, "\n")
    marker := strings.TrimSpace(lines[len(lines)-1])
    if !strings.HasPrefix(marker, "AIW_VERDICT:") { return nil, errors.New("COMPLETION_VERDICT_INVALID") }
    verdict := strings.TrimSpace(strings.TrimPrefix(marker, "AIW_VERDICT:"))
    if verdict != "PASS" && verdict != "REPAIR_REQUIRED" { return nil, errors.New("COMPLETION_VERDICT_INVALID") }
    return &CompletionReport{Verdict: verdict, Summary: summary}, nil
}

func (c *Client) claim(ctx context.Context) (*ClaimedTask, error) {
	var task ClaimedTask
	status, err := c.postStatus(ctx, "/tasks/claim", map[string]any{"workerId": c.cfg.WorkerID}, &task)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNoContent {
		return nil, nil
	}
	return &task, nil
}

func (c *Client) action(ctx context.Context, task ClaimedTask, action, sessionID string) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken, "externalSessionId": nullable(sessionID)}
	return c.post(ctx, "/tasks/"+url.PathEscape(task.TaskID)+"/"+action, payload, nil)
}

func (c *Client) heartbeat(ctx context.Context, task ClaimedTask, sessionID string, out any) error {
    payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken, "externalSessionId": nullable(sessionID)}
    return c.post(ctx, "/tasks/"+url.PathEscape(task.TaskID)+"/heartbeat", payload, out)
}

func (c *Client) start(ctx context.Context, task ClaimedTask, selection *NamedAIProfile, reason string) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken}
	if selection != nil {
		payload["profileId"], payload["profileVersion"], payload["selectionReason"] = selection.ID, task.AIGuidance.Version, reason
	}
	return c.post(ctx, "/tasks/"+url.PathEscape(task.TaskID)+"/start", payload, nil)
}

func (c *Client) fail(ctx context.Context, task ClaimedTask, sessionID, failureReason string, categories ...string) error {
	category := "UNKNOWN"
	if len(categories) > 0 { category = categories[0] }
	payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken, "externalSessionId": nullable(sessionID), "failureReason": failureReason, "failureCategory": category}
	return c.post(ctx, "/tasks/"+url.PathEscape(task.TaskID)+"/fail", payload, nil)
}

func (c *Client) complete(ctx context.Context, task ClaimedTask, sessionID string, workspaceResult *WorkspaceResult, report *CompletionReport) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken, "externalSessionId": nullable(sessionID), "workspaceResult": workspaceResult, "report": report}
	return c.post(ctx, "/tasks/"+url.PathEscape(task.TaskID)+"/complete", payload, nil)
}

func (c *Client) post(ctx context.Context, path string, payload any, out any) error {
	_, err := c.postStatus(ctx, path, payload, out)
	return err
}

func (c *Client) postStatus(ctx context.Context, path string, payload any, out any) (int, error) {
	var body io.Reader
	if payload != nil {
		data, err := json.Marshal(payload)
		if err != nil {
			return 0, err
		}
		body = bytes.NewReader(data)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.cfg.BaseURL+"/api/worker-protocol/v1"+path, body)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Accept", "application/json")
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c.cfg.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.cfg.Token)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		b, _ := io.ReadAll(resp.Body)
		return resp.StatusCode, protocolError{resp.StatusCode, string(b)}
	}
	if resp.StatusCode == http.StatusNoContent || out == nil {
		io.Copy(io.Discard, resp.Body)
		return resp.StatusCode, nil
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return resp.StatusCode, err
	}
	return resp.StatusCode, nil
}

func materializeWorkspace(ctx context.Context, cfg Config, task ClaimedTask, recorder *AttemptEvidence) (*GitWorkspace, error) {
	if task.Workspace == nil {
		return &GitWorkspace{cfg: cfg, cwd: cfg.FallbackWorkspace, ctx: ctx, recorder: recorder}, nil
	}
	repositoryRoot := task.Workspace.RepositoryPath
	if repositoryRoot == "" && (cfg.RepositoryRef == "" || cfg.RepositoryRoot == "") {
		return nil, errors.New("repository task claimed without AIW_REPOSITORY_REF/AIW_REPOSITORY_ROOT")
	}
	if repositoryRoot == "" && task.Workspace.RepositoryRef != cfg.RepositoryRef {
		return nil, fmt.Errorf("repository mismatch: task=%s worker=%s", task.Workspace.RepositoryRef, cfg.RepositoryRef)
	}
	if repositoryRoot == "" { repositoryRoot = cfg.RepositoryRoot }
	repositoryRoot, err := filepath.Abs(repositoryRoot)
	if err != nil { return nil, err }
	info, err := os.Stat(repositoryRoot)
	if err != nil || !info.IsDir() { return nil, errors.New("WORKSPACE_DIRECTORY_INVALID") }
	if task.Workspace.BaseRevision == "" {
		return &GitWorkspace{cfg: cfg, cwd: repositoryRoot, repositoryRoot: repositoryRoot,
			assignment: task.Workspace, ctx: ctx, recorder: recorder}, nil
	}
	release, lockErr := lockWorkspace(ctx, repositoryRoot); if lockErr != nil { return nil, lockErr }; defer release()
    if err := fetchPublishedInputs(ctx, repositoryRoot, task); err != nil {
		return nil, err
	}
	resolved, err := gitTextContext(ctx, repositoryRoot, "rev-parse", "--verify", task.Workspace.BaseRevision+"^{commit}")
	if err != nil {
		return nil, err
	}
	if strings.ToLower(strings.TrimSpace(resolved)) != strings.ToLower(task.Workspace.BaseRevision) {
		return nil, fmt.Errorf("base revision mismatch: expected %s resolved %s", task.Workspace.BaseRevision, strings.TrimSpace(resolved))
	}
	if err := os.MkdirAll(cfg.WorktreeRoot, 0o755); err != nil {
		return nil, err
	}
	cwd, err := filepath.Abs(filepath.Join(cfg.WorktreeRoot, task.Workspace.WorkspaceKey))
    if err != nil || !childPath(cfg.WorktreeRoot, cwd) { return nil, errors.New("WORKSPACE_KEY_INVALID") }
    if err = safeDirectories(filepath.Dir(cwd)); err != nil { return nil, err }
	if _, err := gitTextContext(ctx, repositoryRoot, "worktree", "add", "-b", task.Workspace.BranchName, cwd, task.Workspace.BaseRevision); err != nil {
		return nil, err
	}
	return &GitWorkspace{cfg: cfg, cwd: cwd, repositoryRoot: repositoryRoot, assignment: task.Workspace, ctx: ctx, recorder: recorder, owned: true}, nil
}

func (w *GitWorkspace) finalize(task ClaimedTask) (*WorkspaceResult, error) {
	if w.assignment == nil || w.assignment.BaseRevision == "" {
		return nil, nil
	}
    if !w.cfg.Profile.ReadOnly {
	if _, err := gitTextContext(w.ctx, w.cwd, "add", "-A"); err != nil {
		return nil, err
	}
	raw, err := gitBytesContext(w.ctx, w.cwd, "diff", "--cached", "--name-only", "-z")
	if err != nil {
		return nil, err
	}
	staged := splitZero(raw)
	if len(staged) > 0 {
		if _, err := gitTextContext(w.ctx, w.cwd, "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost", "commit", "--no-gpg-sign", "-m", fmt.Sprintf("aidev: %s attempt %d", task.TaskID, task.AttemptNo)); err != nil {
			return nil, err
		}
	}
    }

	revision, err := gitTextContext(w.ctx, w.cwd, "rev-parse", "HEAD")
	if err != nil {
		return nil, err
	}
	resultRevision := strings.ToLower(strings.TrimSpace(revision))
	diffRaw, err := gitBytesContext(w.ctx, w.cwd, "diff", "--name-only", "-z", w.assignment.BaseRevision, resultRevision)
	if err != nil {
		return nil, err
	}
	changed := splitZero(diffRaw)
	if e := w.recorder.record("workspace-result", map[string]any{"resultRevision": resultRevision, "count": len(changed)}); e != nil { return nil, e }
    return &WorkspaceResult{ResultRevision: resultRevision, ChangedFiles: changed}, nil
}

func fetchPublishedInputs(ctx context.Context, repositoryRoot string, task ClaimedTask) error {
	if repositoryRoot == "" {
		return nil
	}
	for _, input := range task.Inputs {
		if !strings.HasPrefix(input.PublicationRef, "git:") {
			continue
		}
		parts := strings.SplitN(input.PublicationRef, ":", 3)
		if len(parts) != 3 {
			continue
		}
		if _, err := gitTextContext(ctx, repositoryRoot, "fetch", parts[1], parts[2]); err != nil {
			return err
		}
	}
	return nil
}

func (w *GitWorkspace) cleanup() error {
    if w == nil || w.assignment == nil || !w.owned { return nil }
    ctx, cancel := context.WithTimeout(context.Background(), time.Duration(positiveInt("AIW_WORKER_CLEANUP_TIMEOUT_MS", 10000))*time.Millisecond); defer cancel()
    _, err := gitTextContext(ctx, w.repositoryRoot, "worktree", "remove", "--force", w.cwd)
    status := "completed"; if err != nil { status = "preserved" }
    return w.recorder.record("workspace-cleanup", map[string]any{"status": status})
}
func gitText(cwd string, args ...string) (string, error) {
	b, err := gitBytes(cwd, args...)
	return string(b), err
}
func gitBytes(cwd string, args ...string) ([]byte, error) {
	command := append([]string{"-C", cwd}, args...)
	cmd := exec.Command("git", command...)
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}
func splitZero(raw []byte) []string {
	parts := bytes.Split(raw, []byte{0})
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if len(p) > 0 {
			out = append(out, string(p))
		}
	}
	sort.Strings(out)
	return out
}

func buildRoleInstruction(cfg Config, task ClaimedTask) string {
	profile := cfg.Profile
	var b strings.Builder
	b.WriteString("Resolved R2 process Skills (Protocol -> Primary):\n")
	for _, skill := range profile.ProtocolSkills {
		b.WriteString(loadSkillPackage(cfg.SkillsRoot, skill, cfg.IncludeReferences))
		b.WriteString("\n")
	}
	if profile.PrimarySkill != "" {
		b.WriteString(loadSkillPackage(cfg.SkillsRoot, profile.PrimarySkill, cfg.IncludeReferences))
		b.WriteString("\n")
	}
	b.WriteString("AI Dev Manager process role: ")
	b.WriteString(profile.Name)
	b.WriteString("\n\n")
	b.WriteString(profile.Instruction)
	b.WriteString("\n\nAuthority boundary:\n")
	b.WriteString("- The Control Plane owns lifecycle state, scheduling, durable IDs, approval and authoritative transitions.\n")
	b.WriteString("- You perform only the bounded task you claimed and return evidence/result text.\n")
	b.WriteString("- Do not invent authoritative IDs, approvals, lifecycle transitions or hidden state.\n")
	b.WriteString("- Never run git push or publish a Git branch. Return the local result revision only.\n")
	if profile.ReadOnly {
		b.WriteString("- This role is read-only: do not modify repository files.\n")
	}
	b.WriteString("\nTask instruction from the Control Plane:\n")
	b.WriteString(task.Instruction)
	return b.String()
}

func taskTypeAllowed(profile RoleProfile, taskType string) bool {
	for _, allowed := range profile.AllowedTaskTypes {
		if allowed == taskType {
			return true
		}
	}
	return false
}

func loadSkillPackage(root, skill string, includeReferences bool) string {
	dir := filepath.Join(root, skill)
	mainPath := filepath.Join(dir, "SKILL.md")
	main, err := os.ReadFile(mainPath)
	must(err)
	var b strings.Builder
	b.WriteString("\n===== R2 SKILL: ")
	b.WriteString(skill)
	b.WriteString(" =====\n")
	b.Write(main)
	b.WriteString("\n")
	if includeReferences {
		refs, err := filepath.Glob(filepath.Join(dir, "references", "*.md"))
		must(err)
		sort.Strings(refs)
		for _, ref := range refs {
			content, err := os.ReadFile(ref)
			must(err)
			b.WriteString("\n--- R2 reference: ")
			b.WriteString(filepath.Base(ref))
			b.WriteString(" ---\n")
			b.Write(content)
			b.WriteString("\n")
		}
	}
	return b.String()
}

func loadRoleProfile(path string) RoleProfile {
	var p RoleProfile
	if filepath.Ext(path) != ".toml" {
		panic("AIW_PROCESS_PROFILE must be a .toml file")
	}
	metadata, err := toml.DecodeFile(path, &p)
	must(err)
	if undecoded := metadata.Undecoded(); len(undecoded) > 0 {
		panic(fmt.Sprintf("unknown AIW_PROCESS_PROFILE fields: %v", undecoded))
	}
	for _, key := range []string{"name", "workerIdPrefix", "roleCapability", "allowedTaskTypes", "protocolSkills", "primarySkill", "instruction", "readOnly", "resultKind"} {
		if !metadata.IsDefined(key) {
			panic("missing AIW_PROCESS_PROFILE field: " + key)
		}
	}
	p.Name = strings.TrimSpace(p.Name)
	p.WorkerIDPrefix = strings.TrimSpace(p.WorkerIDPrefix)
	p.RoleCapability = strings.TrimSpace(p.RoleCapability)
	p.Instruction = strings.TrimSpace(p.Instruction)
	if p.Name == "" || p.WorkerIDPrefix == "" || p.RoleCapability == "" || len(p.AllowedTaskTypes) == 0 || len(p.ProtocolSkills) == 0 || p.PrimarySkill == "" || p.Instruction == "" {
		panic("invalid AIW_PROCESS_PROFILE: name, workerIdPrefix, roleCapability, allowedTaskTypes, protocolSkills, primarySkill and instruction are required")
	}
	if len(p.AllowedTaskTypes) != 1 || p.AllowedTaskTypes[0] != "EXTERNAL_WORK" {
		panic("invalid AIW_PROCESS_PROFILE: allowedTaskTypes must be [EXTERNAL_WORK]")
	}
	if p.ResultKind != "REPORT" && p.ResultKind != "VERDICT" && p.ResultKind != "WORKSPACE" {
		panic("invalid AIW_PROCESS_PROFILE: unknown resultKind")
	}
	if (p.ResultKind == "WORKSPACE") != !p.ReadOnly {
		panic("invalid AIW_PROCESS_PROFILE: resultKind conflicts with readOnly")
	}
	return p
}

func loadConfig() Config {
    if strings.TrimSpace(os.Getenv("AIW_GIT_PUBLISH_REMOTE")) != "" { panic("AIW_GIT_PUBLISH_REMOTE is forbidden: Workers cannot git push") }
    if boolEnv("AIW_CODEX_NETWORK_ACCESS", false) { panic("AIW_CODEX_NETWORK_ACCESS is forbidden for process Workers") }
    if boolEnv("AIW_AGENT_ALLOW_SHELL", false) { panic("AIW_AGENT_ALLOW_SHELL is forbidden for process Workers") }
	runtime := strings.TrimSpace(env("AIW_AGENT_RUNTIME", ""))
	if runtime != "codex-cli" && runtime != "codex-app-server" && runtime != "openai-api" {
		panic("AIW_AGENT_RUNTIME must explicitly be codex-cli, codex-app-server or openai-api")
	}
	profilePath := strings.TrimSpace(env("AIW_PROCESS_PROFILE", ""))
	if profilePath == "" {
		panic("AIW_PROCESS_PROFILE is required")
	}
	profilePathAbs, err := filepath.Abs(profilePath)
	must(err)
	profile := loadRoleProfile(profilePathAbs)
    if profile.Name == "release-readiness-manager" || profile.Name == "software-development-orchestrator" { panic("ROLE_NONEXECUTABLE") }
	defaultSkillsRoot := filepath.Join(filepath.Dir(profilePathAbs), "..", "skills")
	skillsRoot, err := filepath.Abs(env("AIW_PROCESS_SKILLS_ROOT", defaultSkillsRoot))
	must(err)
	repositoryRef := strings.TrimSpace(env("AIW_REPOSITORY_REF", ""))
	repositoryRootRaw := strings.TrimSpace(env("AIW_REPOSITORY_ROOT", ""))
	if (repositoryRef == "") != (repositoryRootRaw == "") {
		panic("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together")
	}
	repositoryRoot := ""
	if repositoryRootRaw != "" {
		repositoryRoot, err = filepath.Abs(repositoryRootRaw)
		must(err)
	}
	fallback, err := filepath.Abs(env("AIW_WORKSPACE", func() string {
		if repositoryRoot != "" {
			return repositoryRoot
		}
		return "."
	}()))
	must(err)
	defaultWorktreeRoot := filepath.Join(filepath.Dir(func() string {
		if repositoryRoot != "" {
			return repositoryRoot
		}
		return fallback
	}()), ".aidev-worktrees")
	worktreeRoot, err := filepath.Abs(env("AIW_WORKTREE_ROOT", defaultWorktreeRoot))
	must(err)
	capabilities := splitSet(env("AIW_WORKER_CAPABILITIES", ""))
    for _, capability := range capabilities {
        if capability == "*" || capability == "flexible-work-v1" || strings.HasPrefix(capability, "repo:") || roleCapability(capability) { panic("ROLE_CAPABILITY_DENIED") }
    }
	capabilities = appendUnique(capabilities, profile.RoleCapability)
	if repositoryRef != "" {
		capabilities = appendUnique(capabilities, "repo:"+repositoryRef)
	}
	sandbox := env("AIW_CODEX_SANDBOX", func() string {
		if profile.ReadOnly {
			return "read-only"
		}
		return "workspace-write"
	}())
	if profile.ReadOnly && sandbox != "read-only" {
		panic("read-only process profile requires AIW_CODEX_SANDBOX=read-only")
	}

	workerID := env("AIW_WORKER_ID", profile.WorkerIDPrefix+"-1")
	if runtime == "openai-api" && strings.TrimSpace(os.Getenv("AIW_WORKER_ID")) == "" { workerID = profile.WorkerIDPrefix + "-api-1" }
	config := Config{
		AgentRuntime: runtime, AppServerVersion: env("AIW_CODEX_APP_SERVER_VERSION", codexAppServerVersion),
		CodexAuthMode: strings.ToLower(strings.TrimSpace(env("AIW_CODEX_AUTH_MODE", "inherit"))),
		CodexAuthHome: strings.TrimSpace(os.Getenv("AIW_CODEX_AUTH_HOME")),
		Profile: profile, ProfilePath: profilePathAbs, SkillsRoot: skillsRoot, IncludeReferences: boolEnv("AIW_PROCESS_INCLUDE_REFERENCES", true),
		BaseURL:  strings.TrimRight(env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080"), "/"),
		WorkerID: workerID, Token: env("AIW_WORKER_TOKEN", ""),
		Capabilities: capabilities, MaxConcurrency: positiveInt("AIW_WORKER_MAX_CONCURRENCY", 1),
		FallbackWorkspace: fallback, RepositoryRef: repositoryRef, RepositoryRoot: repositoryRoot, WorktreeRoot: worktreeRoot,
		PollInterval:      time.Duration(positiveInt("AIW_WORKER_POLL_MS", 1000)) * time.Millisecond,
		HeartbeatInterval: time.Duration(positiveInt("AIW_WORKER_HEARTBEAT_MS", 20000)) * time.Millisecond,
		HTTPTimeout:       time.Duration(positiveInt("AIW_WORKER_HTTP_TIMEOUT_MS", 15000)) * time.Millisecond,
		CodexPath:         env("AIW_CODEX_PATH", "codex"), Model: env("AIW_CODEX_MODEL", ""), Sandbox: sandbox,
		ApprovalPolicy: env("AIW_CODEX_APPROVAL_POLICY", "never"), ReasoningEffort: env("AIW_CODEX_REASONING_EFFORT", "high"),
		NetworkAccess: false,
		OpenAIAPIKey: strings.TrimSpace(os.Getenv("OPENAI_API_KEY")),
		OpenAIBaseURL: strings.TrimRight(env("OPENAI_BASE_URL", "https://api.openai.com/v1"), "/"),
		OpenAIModel: env("AIW_OPENAI_MODEL", "gpt-4.1"),
		OpenAIReasoningEffort: env("AIW_OPENAI_REASONING_EFFORT", ""),
		OpenAITimeout: time.Duration(positiveInt("AIW_OPENAI_TIMEOUT_MS", 120000)) * time.Millisecond,
		AgentMaxTurns: positiveInt("AIW_AGENT_MAX_TURNS", 40),
		AgentMaxOutputBytes: positiveInt("AIW_AGENT_MAX_OUTPUT_BYTES", 24000),
		AgentToolTimeout: time.Duration(positiveInt("AIW_AGENT_TOOL_TIMEOUT_MS", 60000)) * time.Millisecond,
		AgentAllowShell: false,
	}
	if runtime == "openai-api" && config.OpenAIAPIKey == "" {
		panic("OPENAI_API_KEY is required for openai-api")
	}
	if runtime == "openai-api" && config.OpenAIReasoningEffort != "" && config.OpenAIReasoningEffort != "none" && config.OpenAIReasoningEffort != "minimal" && config.OpenAIReasoningEffort != "low" && config.OpenAIReasoningEffort != "medium" && config.OpenAIReasoningEffort != "high" {
		panic("unsupported AIW_OPENAI_REASONING_EFFORT")
	}
	if config.AgentMaxOutputBytes < 512 {
		panic("AIW_AGENT_MAX_OUTPUT_BYTES must be at least 512")
	}
	return config
}

func env(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}
func positiveInt(name string, fallback int) int {
	v := env(name, strconv.Itoa(fallback))
	n, err := strconv.Atoi(v)
	if err != nil || n < 1 {
		panic(name + " must be >= 1")
	}
	return n
}
func boolEnv(name string, fallback bool) bool {
	v := strings.ToLower(env(name, strconv.FormatBool(fallback)))
	return v == "1" || v == "true" || v == "yes"
}
func splitSet(s string) []string {
	seen := map[string]bool{}
	out := []string{}
	for _, p := range strings.Split(s, ",") {
		p = strings.TrimSpace(p)
		if p != "" && !seen[p] {
			seen[p] = true
			out = append(out, p)
		}
	}
	return out
}
func appendUnique(values []string, value string) []string {
	for _, existing := range values {
		if existing == value {
			return values
		}
	}
	return append(values, value)
}
func nullable(s string) any {
	if s == "" {
		return nil
	}
	return s
}
func sleepContext(ctx context.Context, d time.Duration) {
	select {
	case <-ctx.Done():
	case <-time.After(d):
	}
}
func must(err error) {
	if err != nil {
		panic(err)
	}
}

func validResultRevision(revision string) bool {
    if len(revision) != 40 && len(revision) != 64 { return false }
    for _, char := range revision { if !(char >= '0' && char <= '9' || char >= 'a' && char <= 'f' || char >= 'A' && char <= 'F') { return false } }
    return true
}
func roleCapability(value string) bool {
    switch value { case "requirement-analysis", "solution-architecture", "implementation-planning", "coding", "integration", "repair", "code-review", "verification", "integration-verification", "workflow-review", "release-readiness", "development-orchestration": return true }
    return false
}
