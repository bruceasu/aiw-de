package main

import (
	"bufio"
	"bytes"
	"context"
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
)

type Config struct {
	BaseURL           string
	WorkerID          string
	Token             string
	Capabilities      []string
	MaxConcurrency    int
	FallbackWorkspace string
	RepositoryRef     string
	RepositoryRoot    string
	WorktreeRoot      string
	GitPublishRemote  string
	PollInterval      time.Duration
	HeartbeatInterval time.Duration
	HTTPTimeout       time.Duration
	CodexPath         string
	Model             string
	Sandbox           string
	ApprovalPolicy    string
	ReasoningEffort   string
	NetworkAccess     bool
}

type WorkspaceAssignment struct {
	RepositoryRef string `json:"repositoryRef"`
	BaseRevision  string `json:"baseRevision"`
	WorkspaceKey  string `json:"workspaceKey"`
	BranchName    string `json:"branchName"`
	RepositoryPath string `json:"repositoryPath"`
	WorkspaceID string `json:"workspaceId"`
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
	TaskID      string               `json:"taskId"`
	TaskType    string               `json:"taskType"`
	SubjectType string               `json:"subjectType"`
	SubjectID   string               `json:"subjectId"`
	Instruction string               `json:"instruction"`
	AttemptID   string               `json:"attemptId"`
	AttemptNo   int                  `json:"attemptNo"`
	LeaseToken  string               `json:"leaseToken"`
	Workspace   *WorkspaceAssignment `json:"workspace"`
	Inputs      []TaskInput          `json:"inputs"`
}

type GitWorkspace struct {
	cfg        Config
	cwd        string
	assignment *WorkspaceAssignment
}

type Client struct {
	cfg  Config
	http *http.Client
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
	client := &Client{cfg: cfg, http: &http.Client{Timeout: cfg.HTTPTimeout}}
	root, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	must(client.register(root))
	fmt.Printf("registered %s (%d slots)\n", cfg.WorkerID, cfg.MaxConcurrency)

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
		executeTask(ctx, slot, client, *task)
	}
}

func executeTask(parent context.Context, slot int, client *Client, task ClaimedTask) {
	if err := client.action(parent, task, "start", ""); err != nil {
		fmt.Fprintf(os.Stderr, "slot %d start %s: %v\n", slot, task.TaskID, err)
		return
	}

	runCtx, cancel := context.WithCancel(parent)
	defer cancel()
	var mu sync.RWMutex
	sessionID := ""
	ownershipLost := false
	done := make(chan struct{})

	go func() {
		ticker := time.NewTicker(client.cfg.HeartbeatInterval)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-runCtx.Done():
				return
			case <-ticker.C:
				mu.RLock()
				sid := sessionID
				mu.RUnlock()
				if err := client.action(runCtx, task, "heartbeat", sid); err != nil {
					mu.Lock()
					ownershipLost = true
					mu.Unlock()
					fmt.Fprintf(os.Stderr, "task %s lease lost: %v\n", task.TaskID, err)
					cancel()
					return
				}
			}
		}
	}()

	workspace, wsErr := materializeWorkspace(client.cfg, task)
	if wsErr != nil {
		close(done)
		_ = client.fail(context.Background(), task, "", wsErr.Error())
		fmt.Fprintf(os.Stderr, "slot %d workspace %s failed: %v\n", slot, task.TaskID, wsErr)
		return
	}
	defer workspace.cleanup()

	instruction := task.Instruction
	if len(task.Inputs) > 0 {
		if b, e := json.MarshalIndent(task.Inputs, "", "  "); e == nil {
			instruction += "\n\nUpstream task inputs (authoritative):\n" + string(b)
		}
	}
	sid, finalMessage, err := runCodex(runCtx, client.cfg, workspace.cwd, instruction, func(id string) {
		mu.Lock()
		sessionID = id
		mu.Unlock()
		if hbErr := client.action(runCtx, task, "heartbeat", id); hbErr != nil {
			mu.Lock()
			ownershipLost = true
			mu.Unlock()
			fmt.Fprintf(os.Stderr, "task %s initial session heartbeat failed: %v\n", task.TaskID, hbErr)
			cancel()
		}
	})
	close(done)
	mu.Lock()
	if sid != "" {
		sessionID = sid
	}
	lost := ownershipLost
	finalSID := sessionID
	mu.Unlock()

	if lost {
		return
	}
	if err != nil {
		if reportErr := client.fail(context.Background(), task, finalSID, err.Error()); reportErr != nil {
			fmt.Fprintf(os.Stderr, "slot %d fail report %s: %v\n", slot, task.TaskID, reportErr)
		}
		fmt.Fprintf(os.Stderr, "slot %d task %s failed: %v\n", slot, task.TaskID, err)
		return
	}
	workspaceResult, err := workspace.finalize(task)
	if err != nil {
		_ = client.fail(context.Background(), task, finalSID, err.Error())
		fmt.Fprintf(os.Stderr, "slot %d finalize %s: %v\n", slot, task.TaskID, err)
		return
	}
	report, err := completionReport(task, finalMessage)
	if err != nil {
		_ = client.fail(context.Background(), task, finalSID, err.Error())
		fmt.Fprintf(os.Stderr, "slot %d task %s completion report failed: %v\n", slot, task.TaskID, err)
		return
	}
	if err := client.complete(context.Background(), task, finalSID, workspaceResult, report); err != nil {
		fmt.Fprintf(os.Stderr, "slot %d complete %s: %v\n", slot, task.TaskID, err)
		return
	}
	rev := "-"
	if workspaceResult != nil {
		rev = workspaceResult.ResultRevision
	}
	fmt.Printf("slot %d completed %s session=%s revision=%s\n", slot, task.TaskID, finalSID, rev)
	if finalMessage != "" {
		fmt.Printf("slot %d: %s\n", slot, finalMessage)
	}
}

func runCodex(ctx context.Context, cfg Config, workspace string, instruction string, onSession func(string)) (string, string, error) {
	args := []string{"exec", "--json", "--sandbox", cfg.Sandbox, "--cd", workspace,
		"--config", fmt.Sprintf("approval_policy=\"%s\"", cfg.ApprovalPolicy),
		"--config", fmt.Sprintf("model_reasoning_effort=\"%s\"", cfg.ReasoningEffort),
		"--config", fmt.Sprintf("sandbox_workspace_write.network_access=%t", cfg.NetworkAccess),
	}
	if cfg.Model != "" {
		args = append(args, "--model", cfg.Model)
	}
	args = append(args, instruction)

	cmd := exec.CommandContext(ctx, cfg.CodexPath, args...)
	cmd.Dir = workspace
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
	go io.Copy(os.Stderr, stderr)

	sessionID := ""
	finalMessage := ""
	terminalFailed := ""
	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for scanner.Scan() {
		line := scanner.Bytes()
		var event map[string]any
		if err := json.Unmarshal(line, &event); err != nil {
			fmt.Fprintf(os.Stderr, "codex non-json stdout: %s\n", string(line))
			continue
		}
		typ, _ := event["type"].(string)
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
				}
			}
		case "turn.failed", "error":
			b, _ := json.Marshal(event)
			terminalFailed = string(b)
		}
	}
	scanErr := scanner.Err()
	waitErr := cmd.Wait()
	if ctx.Err() != nil {
		return sessionID, finalMessage, ctx.Err()
	}
	if scanErr != nil {
		return sessionID, finalMessage, scanErr
	}
	if terminalFailed != "" {
		return sessionID, finalMessage, errors.New(terminalFailed)
	}
	if waitErr != nil {
		return sessionID, finalMessage, waitErr
	}
	return sessionID, finalMessage, nil
}

func (c *Client) register(ctx context.Context) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "runtimeType": "codex-go-cli", "capabilities": c.cfg.Capabilities, "maxConcurrency": c.cfg.MaxConcurrency}
	return c.post(ctx, "/workers/register", payload, nil)
}

func completionReport(task ClaimedTask, finalMessage string) (*CompletionReport, error) {
	if task.TaskType == "REQUIREMENT_ANALYSIS" || task.TaskType == "SOLUTION_ARCHITECTURE" || task.TaskType == "IMPLEMENTATION_PLANNING" {
		summary := strings.TrimSpace(finalMessage)
		if summary == "" {
			return nil, fmt.Errorf("%s must return a non-empty final response", task.TaskType)
		}
		return &CompletionReport{Verdict: "PASS", Summary: summary}, nil
	}
	if task.TaskType != "CODE_REVIEW" && task.TaskType != "VERIFICATION" && task.TaskType != "INTEGRATION_VERIFICATION" {
		return nil, nil
	}
	verdict := ""
	lines := strings.Split(finalMessage, "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		line := strings.TrimSpace(lines[i])
		if strings.HasPrefix(line, "AIW_VERDICT:") {
			verdict = strings.TrimSpace(strings.TrimPrefix(line, "AIW_VERDICT:"))
			break
		}
	}
	if verdict != "PASS" && verdict != "REPAIR_REQUIRED" {
		return nil, errors.New("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED")
	}
	summary := strings.TrimSpace(finalMessage)
	if summary == "" {
		summary = "AIW_VERDICT: " + verdict
	}
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

func (c *Client) fail(ctx context.Context, task ClaimedTask, sessionID, failureReason string) error {
	payload := map[string]any{"workerId": c.cfg.WorkerID, "leaseToken": task.LeaseToken, "externalSessionId": nullable(sessionID), "failureReason": failureReason}
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

func materializeWorkspace(cfg Config, task ClaimedTask) (*GitWorkspace, error) {
	if task.Workspace == nil {
		return &GitWorkspace{cfg: cfg, cwd: cfg.FallbackWorkspace}, nil
	}
	if task.Workspace.RepositoryPath != "" {
		cfg.RepositoryRoot = task.Workspace.RepositoryPath
	}
	if cfg.RepositoryRoot == "" {
		return nil, errors.New("repository task claimed without AIW_REPOSITORY_REF/AIW_REPOSITORY_ROOT")
	}
	if task.Workspace.RepositoryPath == "" && task.Workspace.RepositoryRef != cfg.RepositoryRef {
		return nil, fmt.Errorf("repository mismatch: task=%s worker=%s", task.Workspace.RepositoryRef, cfg.RepositoryRef)
	}
	if task.Workspace.BaseRevision == "" {
		cwd, err := filepath.Abs(cfg.RepositoryRoot)
		if err != nil { return nil, err }
		info, err := os.Stat(cwd)
		if err != nil { return nil, err }
		if !info.IsDir() { return nil, fmt.Errorf("workspace is not a directory: %s", cwd) }
		return &GitWorkspace{cfg: cfg, cwd: cwd, assignment: task.Workspace}, nil
	}
	if err := fetchPublishedInputs(cfg, task); err != nil {
		return nil, err
	}
	resolved, err := gitText(cfg.RepositoryRoot, "rev-parse", "--verify", task.Workspace.BaseRevision+"^{commit}")
	if err != nil {
		return nil, err
	}
	if strings.ToLower(strings.TrimSpace(resolved)) != strings.ToLower(task.Workspace.BaseRevision) {
		return nil, fmt.Errorf("base revision mismatch: expected %s resolved %s", task.Workspace.BaseRevision, strings.TrimSpace(resolved))
	}
	if err := os.MkdirAll(cfg.WorktreeRoot, 0o755); err != nil {
		return nil, err
	}
	cwd := filepath.Join(cfg.WorktreeRoot, task.Workspace.WorkspaceKey)
	if _, err := gitText(cfg.RepositoryRoot, "worktree", "add", "-b", task.Workspace.BranchName, cwd, task.Workspace.BaseRevision); err != nil {
		return nil, err
	}
	return &GitWorkspace{cfg: cfg, cwd: cwd, assignment: task.Workspace}, nil
}

func (w *GitWorkspace) finalize(task ClaimedTask) (*WorkspaceResult, error) {
	if w.assignment == nil || w.assignment.BaseRevision == "" {
		return nil, nil
	}
	if _, err := gitText(w.cwd, "add", "-A"); err != nil {
		return nil, err
	}
	raw, err := gitBytes(w.cwd, "diff", "--cached", "--name-only", "-z")
	if err != nil {
		return nil, err
	}
	staged := splitZero(raw)
	if len(staged) > 0 {
		if _, err := gitText(w.cwd, "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost", "commit", "--no-gpg-sign", "-m", fmt.Sprintf("aidev: %s attempt %d", task.TaskID, task.AttemptNo)); err != nil {
			return nil, err
		}
	}
	revision, err := gitText(w.cwd, "rev-parse", "HEAD")
	if err != nil {
		return nil, err
	}
	resultRevision := strings.ToLower(strings.TrimSpace(revision))
	diffRaw, err := gitBytes(w.cwd, "diff", "--name-only", "-z", w.assignment.BaseRevision, resultRevision)
	if err != nil {
		return nil, err
	}
	changed := splitZero(diffRaw)
	publicationRef := ""
	if w.cfg.GitPublishRemote != "" {
		if _, err := gitText(w.cwd, "push", w.cfg.GitPublishRemote, resultRevision+":refs/heads/"+w.assignment.BranchName); err != nil {
			return nil, err
		}
		publicationRef = "git:" + w.cfg.GitPublishRemote + ":" + w.assignment.BranchName
	}
	return &WorkspaceResult{ResultRevision: resultRevision, ChangedFiles: changed, PublicationRef: publicationRef}, nil
}

func fetchPublishedInputs(cfg Config, task ClaimedTask) error {
	if cfg.RepositoryRoot == "" {
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
		if _, err := gitText(cfg.RepositoryRoot, "fetch", parts[1], parts[2]); err != nil {
			return err
		}
	}
	return nil
}

func (w *GitWorkspace) cleanup() {
	if w == nil || w.assignment == nil || w.assignment.BaseRevision == "" {
		return
	}
	if _, err := gitText(w.cfg.RepositoryRoot, "worktree", "remove", "--force", w.cwd); err != nil {
		fmt.Fprintf(os.Stderr, "worktree cleanup failed %s: %v\n", w.cwd, err)
	}
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

func loadConfig() Config {
	repositoryRef := strings.TrimSpace(env("AIW_REPOSITORY_REF", ""))
	repositoryRootRaw := strings.TrimSpace(env("AIW_REPOSITORY_ROOT", ""))
	if (repositoryRef == "") != (repositoryRootRaw == "") {
		panic("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together")
	}
	repositoryRoot := ""
	var err error
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
	capabilities := splitSet(env("AIW_WORKER_CAPABILITIES", "coding"))
	if repositoryRef != "" {
		capabilities = appendUnique(capabilities, "repo:"+repositoryRef)
	}
	return Config{
		BaseURL:  strings.TrimRight(env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080"), "/"),
		WorkerID: env("AIW_WORKER_ID", "codex-go-1"), Token: env("AIW_WORKER_TOKEN", ""),
		Capabilities: capabilities, MaxConcurrency: positiveInt("AIW_WORKER_MAX_CONCURRENCY", 1),
		FallbackWorkspace: fallback, RepositoryRef: repositoryRef, RepositoryRoot: repositoryRoot, WorktreeRoot: worktreeRoot, GitPublishRemote: env("AIW_GIT_PUBLISH_REMOTE", ""),
		PollInterval:      time.Duration(positiveInt("AIW_WORKER_POLL_MS", 1000)) * time.Millisecond,
		HeartbeatInterval: time.Duration(positiveInt("AIW_WORKER_HEARTBEAT_MS", 20000)) * time.Millisecond,
		HTTPTimeout:       time.Duration(positiveInt("AIW_WORKER_HTTP_TIMEOUT_MS", 15000)) * time.Millisecond,
		CodexPath:         env("AIW_CODEX_PATH", "codex"), Model: env("AIW_CODEX_MODEL", ""), Sandbox: env("AIW_CODEX_SANDBOX", "workspace-write"),
		ApprovalPolicy: env("AIW_CODEX_APPROVAL_POLICY", "never"), ReasoningEffort: env("AIW_CODEX_REASONING_EFFORT", "high"),
		NetworkAccess: boolEnv("AIW_CODEX_NETWORK_ACCESS", false),
	}
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
