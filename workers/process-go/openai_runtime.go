package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
)

type OpenAIAgentRuntime struct {
	cfg Config
	client *http.Client
}

func NewOpenAIAgentRuntime(cfg Config) *OpenAIAgentRuntime {
	return &OpenAIAgentRuntime{cfg: cfg, client: &http.Client{Timeout: cfg.OpenAITimeout}}
}

func (*OpenAIAgentRuntime) Type() string { return "openai-api" }
func (*OpenAIAgentRuntime) Capabilities() AgentRuntimeCapabilities {
	return AgentRuntimeCapabilities{AttemptCancellation: true, Tools: true}
}

type responseUsage struct {
	InputTokens int64 `json:"input_tokens"`
	OutputTokens int64 `json:"output_tokens"`
}

type responseEnvelope struct {
	ID string `json:"id"`
	Status string `json:"status"`
	Output []json.RawMessage `json:"output"`
	Usage responseUsage `json:"usage"`
	Error *struct { Message string `json:"message"` } `json:"error"`
}

type responseOutputItem struct {
	Type string `json:"type"`
	ID string `json:"id"`
	CallID string `json:"call_id"`
	Name string `json:"name"`
	Arguments string `json:"arguments"`
	Content []struct { Type string `json:"type"`; Text string `json:"text"` } `json:"content"`
}

type functionResult struct {
	CallID string `json:"call_id"`
	Output string `json:"output"`
	Type string `json:"type"`
}

func (r *OpenAIAgentRuntime) Run(ctx context.Context, request AgentRunRequest) (AgentRunResult, error) {
	if request.OnSession != nil { request.OnSession("openai:" + request.Task.AttemptID) }
	cfg := r.cfg
	model, effort := cfg.OpenAIModel, cfg.OpenAIReasoningEffort
	if request.Selection != nil { model, effort = request.Selection.Profile.Model, request.Selection.Profile.Effort }
	input := []any{map[string]any{"role":"user", "content": request.Instruction}}
	result := AgentRunResult{SessionID:"openai:" + request.Task.AttemptID}
	for turn := 0; turn < cfg.AgentMaxTurns; turn++ {
		body := map[string]any{"model":model, "input":input, "tools":openAITools(cfg.AgentAllowShell, request.ReadOnly), "tool_choice":"auto",
			"parallel_tool_calls":false, "store":false, "max_output_tokens":4096}
		if supportsReasoning(model) && effort != "" && effort != "none" { body["reasoning"] = map[string]any{"effort":effort} }
		response, err := r.createResponse(ctx, body)
		if err != nil { if turn == 0 && aiPreferencesRejected(err) { return result, errAIPreferencesRejected }; return result, err }
		if response.Status != "completed" { return result, errors.New("OPENAI_RESPONSE_NOT_COMPLETED") }
		result.InputTokens += response.Usage.InputTokens
		result.OutputTokens += response.Usage.OutputTokens
		calls := make([]responseOutputItem, 0)
		var final strings.Builder
		for _, raw := range response.Output {
			var item responseOutputItem
			if err := json.Unmarshal(raw, &item); err != nil { return result, errors.New("OPENAI_RESPONSE_INVALID") }
			if item.Type == "function_call" { calls = append(calls, item) }
			if item.Type == "message" {
				for _, part := range item.Content { if part.Type == "output_text" { final.WriteString(part.Text) } }
			}
		}
		if len(calls) == 0 {
			result.Output = strings.TrimSpace(final.String())
			result.UsageKnown = response.Usage.InputTokens > 0 || response.Usage.OutputTokens > 0
			if request.Evidence != nil {
				values := map[string]any{"status":"completed", "usageKnown":result.UsageKnown}
				if result.UsageKnown { values["inputTokens"], values["outputTokens"] = result.InputTokens, result.OutputTokens }
				if err := request.Evidence.record("model-terminal", values); err != nil { return result, err }
			}
			if result.Output == "" { return result, errors.New("OPENAI_OUTPUT_EMPTY") }
			return result, nil
		}
		for _, raw := range response.Output { input = append(input, json.RawMessage(raw)) }
		for _, call := range calls {
			if call.CallID == "" || call.Name == "" { return result, errors.New("OPENAI_FUNCTION_CALL_INVALID") }
			var args map[string]any
			if json.Unmarshal([]byte(call.Arguments), &args) != nil { return result, errors.New("OPENAI_FUNCTION_ARGUMENTS_INVALID") }
			output, callErr := runWorkspaceTool(ctx, cfg, request, call.Name, args)
			if callErr != nil && callErr.Error() == "PROCESS_TREE_UNCERTAIN" { return result, callErr }
			status := "ok"; if callErr != nil { status = "error"; output = callErr.Error() }
			if request.Evidence != nil {
				if err := request.Evidence.record("sdk-item-terminal", map[string]any{"callId":call.CallID, "tool":call.Name, "status":status}); err != nil { return result, err }
			}
			input = append(input, functionResult{Type:"function_call_output", CallID:call.CallID, Output:output})
		}
	}
	return result, errors.New("AGENT_MAX_TURNS_EXCEEDED")
}

func (r *OpenAIAgentRuntime) createResponse(ctx context.Context, body map[string]any) (responseEnvelope, error) {
	raw, err := json.Marshal(body); if err != nil { return responseEnvelope{}, err }
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, r.cfg.OpenAIBaseURL+"/responses", bytes.NewReader(raw)); if err != nil { return responseEnvelope{}, err }
	req.Header.Set("Authorization", "Bearer " + r.cfg.OpenAIAPIKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := r.client.Do(req); if err != nil { return responseEnvelope{}, err }; defer resp.Body.Close()
	payload, err := io.ReadAll(io.LimitReader(resp.Body, 8*1024*1024+1)); if err != nil { return responseEnvelope{}, err }
	if len(payload) > 8*1024*1024 { return responseEnvelope{}, errors.New("OPENAI_RESPONSE_TOO_LARGE") }
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		var failure struct { Error struct { Message string `json:"message"` } `json:"error"` }
		_ = json.Unmarshal(payload, &failure)
		if failure.Error.Message == "" { return responseEnvelope{}, fmt.Errorf("OPENAI_API_HTTP_%d", resp.StatusCode) }
		return responseEnvelope{}, fmt.Errorf("OPENAI_API_HTTP_%d: %s", resp.StatusCode, failure.Error.Message)
	}
	var result responseEnvelope
	if json.Unmarshal(payload, &result) != nil || result.ID == "" || result.Error != nil { return responseEnvelope{}, errors.New("OPENAI_RESPONSE_INVALID") }
	return result, nil
}

func supportsReasoning(model string) bool {
	return strings.HasPrefix(model, "o1") || strings.HasPrefix(model, "o3") || strings.HasPrefix(model, "o4") || strings.HasPrefix(model, "gpt-5")
}

func openAITools(allowShell, readOnly bool) []any {
	tools := []any{
		openAIFunction("read_file", "Read a UTF-8 text file by one-based line offset and line limit.", map[string]any{"path":stringSchema(), "offset":map[string]any{"type":"integer","minimum":1}, "limit":map[string]any{"type":"integer","minimum":1,"maximum":1000}}, []string{"path","offset","limit"}),
		openAIFunction("list_files", "List visible files and directories under a workspace-relative path.", map[string]any{"path":stringSchema()}, []string{"path"}),
		openAIFunction("search_text", "Search literal text in visible UTF-8 workspace files.", map[string]any{"path":stringSchema(), "text":stringSchema()}, []string{"path","text"}),
	}
	if !readOnly {
		tools = append(tools,
			openAIFunction("write_file", "Create or replace a UTF-8 workspace file.", map[string]any{"path":stringSchema(), "content":stringSchema()}, []string{"path","content"}),
			openAIFunction("edit_file", "Replace one unique exact text occurrence in a UTF-8 workspace file.", map[string]any{"path":stringSchema(), "old_text":stringSchema(), "new_text":stringSchema()}, []string{"path","old_text","new_text"}),
		)
		if allowShell { tools = append(tools, openAIFunction("run_command", "Run an operator-authorized foreground command in the workspace. Host permissions apply.", map[string]any{"command":stringSchema()}, []string{"command"})) }
	}
	return tools
}

func stringSchema() map[string]any { return map[string]any{"type":"string"} }
func openAIFunction(name, description string, properties map[string]any, required []string) any {
	return map[string]any{"type":"function", "name":name, "description":description,
		"parameters":map[string]any{"type":"object", "properties":properties, "required":required, "additionalProperties":false}, "strict":true}
}

func runWorkspaceTool(ctx context.Context, cfg Config, request AgentRunRequest, name string, args map[string]any) (string, error) {
	getString := func(key string) (string, error) { value, ok := args[key].(string); if !ok { return "", errors.New("TOOL_ARGUMENT_INVALID") }; return value, nil }
	switch name {
	case "read_file":
		rel, err := getString("path"); if err != nil { return "", err }; file, err := workspaceFile(request.Workspace, rel, false); if err != nil { return "", err }
		raw, err := os.ReadFile(file); if err != nil { return "", err }; if len(raw) > 2*1024*1024 { return "", errors.New("READ_FILE_TOO_LARGE") }
		offset, ok1 := args["offset"].(float64); limit, ok2 := args["limit"].(float64); if !ok1 || !ok2 || offset < 1 || limit < 1 || limit > 1000 { return "", errors.New("TOOL_ARGUMENT_INVALID") }
		lines := strings.Split(strings.ReplaceAll(string(raw), "\r\n", "\n"), "\n"); start := int(offset)-1; if start >= len(lines) { return "", nil }; end := start+int(limit); if end > len(lines) { end = len(lines) }
		for i := start; i < end; i++ { lines[i] = fmt.Sprintf("%d: %s", i+1, lines[i]) }; return boundOutput(strings.Join(lines[start:end], "\n"), cfg.AgentMaxOutputBytes), nil
	case "list_files":
		rel, err := getString("path"); if err != nil { return "", err }; directory, err := workspaceFile(request.Workspace, rel, false); if err != nil { return "", err }; entries, err := os.ReadDir(directory); if err != nil { return "", err }
		values := make([]string, 0, len(entries)); for _, entry := range entries { if !visibleEntry(entry.Name()) || entry.Type()&os.ModeSymlink != 0 { continue }; name := entry.Name(); if entry.IsDir() { name += "/" }; values = append(values, name) }; sort.Strings(values); return boundOutput(strings.Join(values, "\n"), cfg.AgentMaxOutputBytes), nil
	case "search_text":
		rel, err := getString("path"); if err != nil { return "", err }; query, err := getString("text"); if err != nil || query == "" { return "", errors.New("TOOL_ARGUMENT_INVALID") }; root, err := workspaceFile(request.Workspace, rel, false); if err != nil { return "", err }
		matches := []string{}; scanned := 0; total := 0
		err = filepath.WalkDir(root, func(file string, entry os.DirEntry, walkErr error) error {
			if walkErr != nil { return walkErr }; if err := ctx.Err(); err != nil { return err }; if entry.IsDir() && file != root && !visibleEntry(entry.Name()) { return filepath.SkipDir }; if entry.Type()&os.ModeSymlink != 0 { return nil }; if entry.IsDir() { return nil }; if !entry.Type().IsRegular() || !visibleEntry(entry.Name()) { return nil }
			scanned++; if scanned > 5000 { return filepath.SkipAll }; info, err := entry.Info(); if err != nil || info.Size() > 512*1024 { return nil }; raw, err := os.ReadFile(file); if err != nil || bytes.IndexByte(raw, 0) >= 0 { return nil }
			for i, line := range strings.Split(strings.ReplaceAll(string(raw), "\r\n", "\n"), "\n") { if strings.Contains(line, query) { rel, _ := filepath.Rel(request.Workspace, file); matches = append(matches, fmt.Sprintf("%s:%d: %s", rel, i+1, line)); total += len(matches[len(matches)-1]); if total >= cfg.AgentMaxOutputBytes { return filepath.SkipAll } } }; return nil
		})
		if err != nil && !errors.Is(err, filepath.SkipAll) { return "", err }; return boundOutput(strings.Join(matches, "\n"), cfg.AgentMaxOutputBytes), nil
	case "write_file":
		rel, err := getString("path"); if err != nil { return "", err }; content, err := getString("content"); if err != nil { return "", err }; if request.ReadOnly { return "", errors.New("READ_ONLY_PROFILE") }; if len(content) > 2*1024*1024 { return "", errors.New("WRITE_FILE_TOO_LARGE") }
		file, err := workspaceFile(request.Workspace, rel, true); if err != nil { return "", err }; if err = os.MkdirAll(filepath.Dir(file), 0755); err != nil { return "", err }; if _, err = workspaceFile(request.Workspace, rel, true); err != nil { return "", err }
		tmp, err := os.CreateTemp(filepath.Dir(file), ".aiw-write-*"); if err != nil { return "", err }; temp := tmp.Name(); defer os.Remove(temp); if _, err = tmp.WriteString(content); err != nil { tmp.Close(); return "", err }; if err = tmp.Close(); err != nil { return "", err }; if err = os.Rename(temp, file); err != nil { return "", err }; return "File written", nil
	case "edit_file":
		rel, err := getString("path"); if err != nil { return "", err }; oldText, err := getString("old_text"); if err != nil || oldText == "" { return "", errors.New("TOOL_ARGUMENT_INVALID") }; newText, err := getString("new_text"); if err != nil { return "", err }; if request.ReadOnly { return "", errors.New("READ_ONLY_PROFILE") }
		file, err := workspaceFile(request.Workspace, rel, true); if err != nil { return "", err }; raw, err := os.ReadFile(file); if err != nil { return "", err }; if len(raw) > 2*1024*1024 { return "", errors.New("EDIT_FILE_TOO_LARGE") }; text := string(raw); if strings.Count(text, oldText) != 1 { return "", errors.New("EDIT_MATCH_NOT_UNIQUE") }; updated := strings.Replace(text, oldText, newText, 1); if err = atomicWorkspaceWrite(file, updated); err != nil { return "", err }; return "File edited", nil
	case "run_command":
		command, err := getString("command"); if err != nil { return "", err }; if !cfg.AgentAllowShell { return "", errors.New("SHELL_DISABLED") }; if request.ReadOnly { return "", errors.New("READ_ONLY_PROFILE") }
		toolCtx, cancel := context.WithTimeout(ctx, cfg.AgentToolTimeout); defer cancel()
		var cmd *exec.Cmd; if filepath.Separator == '\\' { cmd = exec.CommandContext(toolCtx, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command) } else { cmd = exec.CommandContext(toolCtx, "/bin/sh", "-c", command) }; cmd.Dir = request.Workspace; cmd.Env = childEnvironment(false); configureProcessTree(cmd); capture := &limitedCapture{}; cmd.Stdout, cmd.Stderr = capture, capture
		err = cmd.Run(); if toolCtx.Err() != nil { return boundOutput(string(capture.data), cfg.AgentMaxOutputBytes), errors.New("PROCESS_TREE_UNCERTAIN") }; if capture.exceeded { return boundOutput(string(capture.data), cfg.AgentMaxOutputBytes), errors.New("COMMAND_OUTPUT_TOO_LARGE") }; if err != nil { return boundOutput(string(capture.data), cfg.AgentMaxOutputBytes), fmt.Errorf("COMMAND_FAILED: %w", err) }; return boundOutput(string(capture.data), cfg.AgentMaxOutputBytes), nil
	default:
		return "", errors.New("TOOL_NOT_ALLOWED")
	}
}

func workspaceFile(root, name string, allowMissing bool) (string, error) {
	if filepath.IsAbs(name) { return "", errors.New("PATH_OUTSIDE_WORKSPACE") }; root, err := filepath.Abs(root); if err != nil { return "", err }; candidate := filepath.Clean(filepath.Join(root, name)); rel, err := filepath.Rel(root, candidate); if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) || filepath.IsAbs(rel) { return "", errors.New("PATH_OUTSIDE_WORKSPACE") }
	for current := candidate; ; current = filepath.Dir(current) { info, statErr := os.Lstat(current); if statErr == nil && info.Mode()&os.ModeSymlink != 0 { return "", errors.New("WORKSPACE_SYMLINK_DENIED") }; if statErr != nil && !os.IsNotExist(statErr) { return "", statErr }; if current == root { break }; parent := filepath.Dir(current); if parent == current || !childPath(root, current) && current != root { return "", errors.New("PATH_OUTSIDE_WORKSPACE") } }
	if !allowMissing { actual, err := filepath.EvalSymlinks(candidate); if err != nil { return "", err }; rel, err := filepath.Rel(root, actual); if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) { return "", errors.New("PATH_OUTSIDE_WORKSPACE") }; return actual, nil }; return candidate, nil
}

func atomicWorkspaceWrite(file, content string) error {
	tmp, err := os.CreateTemp(filepath.Dir(file), ".aiw-write-*"); if err != nil { return err }; name := tmp.Name(); defer os.Remove(name); if _, err = tmp.WriteString(content); err != nil { tmp.Close(); return err }; if err = tmp.Close(); err != nil { return err }; return os.Rename(name, file)
}

func visibleEntry(name string) bool { return !strings.HasPrefix(name, ".") && name != "node_modules" && name != "dist" && name != "out" && name != "target" && name != "__pycache__" }
func boundOutput(value string, max int) string { if len(value) <= max { return value }; return value[:max] + "\n[truncated]" }
