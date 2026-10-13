package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

func codexAuthMode(cfg Config) string {
	if cfg.CodexAuthMode == "" {
		return "inherit"
	}
	return cfg.CodexAuthMode
}

func validateCodexAuthConfig(cfg Config) error {
	mode := codexAuthMode(cfg)
	if mode != "inherit" && mode != "api-key" && mode != "chatgpt" {
		return errors.New("unsupported AIW_CODEX_AUTH_MODE")
	}
	if mode != "inherit" && cfg.AgentRuntime == "openai-api" {
		return errors.New("AIW_CODEX_AUTH_MODE applies only to Codex runtimes")
	}
	return validateCodexAuthHome(cfg, "")
}

func validateCodexAuthHome(cfg Config, workspace string) error {
	if codexAuthMode(cfg) == "inherit" {
		return nil
	}
	if !filepath.IsAbs(cfg.CodexAuthHome) {
		return errors.New("explicit Codex authentication requires absolute AIW_CODEX_AUTH_HOME")
	}
	home, err := filepath.EvalSymlinks(cfg.CodexAuthHome)
	if err != nil {
		return errors.New("CODEX_AUTH_HOME_UNAVAILABLE")
	}
	info, err := os.Stat(home)
	if err != nil || !info.IsDir() {
		return errors.New("CODEX_AUTH_HOME_UNAVAILABLE")
	}
	for _, root := range []string{cfg.RepositoryRoot, cfg.FallbackWorkspace, cfg.WorktreeRoot, workspace} {
		if root == "" {
			continue
		}
		absolute, err := filepath.Abs(root)
		if err != nil {
			return errors.New("CODEX_AUTH_WORKSPACE_INVALID")
		}
		if resolved, err := filepath.EvalSymlinks(absolute); err == nil {
			absolute = resolved
		}
		relative, err := filepath.Rel(absolute, home)
		if err == nil && relative != ".." && !strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
			return errors.New("CODEX_AUTH_HOME_MUST_BE_OUTSIDE_WORKSPACE")
		}
	}
	return nil
}

// Explicit modes select a prepared native credential context. They never
// copy credentials, invoke login/logout, or inject forced_login_method.
func codexChildEnvironment(cfg Config) []string {
	environment := childEnvironment(true)
	if codexAuthMode(cfg) == "inherit" {
		return environment
	}
	selected := make([]string, 0, len(environment)+1)
	for _, item := range environment {
		key := strings.ToUpper(strings.SplitN(item, "=", 2)[0])
		switch key {
		case "CODEX_HOME", "OPENAI_API_KEY", "CODEX_API_KEY", "CODEX_ACCESS_TOKEN":
			continue
		}
		selected = append(selected, item)
	}
	return append(selected, "CODEX_HOME="+cfg.CodexAuthHome)
}

func checkCodexAuthentication(ctx context.Context, cfg Config, workspace string) error {
	if err := validateCodexAuthConfig(cfg); err != nil {
		return err
	}
	if err := validateCodexAuthHome(cfg, workspace); err != nil {
		return err
	}
	mode := codexAuthMode(cfg)
	if mode == "inherit" {
		return nil
	}
	bounded, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	cmd := exec.CommandContext(bounded, cfg.CodexPath, "login", "status")
	cmd.Dir = cfg.CodexAuthHome
	cmd.Env = codexChildEnvironment(cfg)
	cmd.Stdout = io.Discard
	status := &limitedCapture{}
	cmd.Stderr = status
	configureProcessTree(cmd)
	if err := cmd.Run(); err != nil || status.exceeded {
		return errors.New("CODEX_AUTH_UNAVAILABLE")
	}
	observed := ""
	for _, line := range strings.Split(string(status.data), "\n") {
		line = strings.TrimSpace(line)
		candidate := ""
		if line == "Logged in using ChatGPT" {
			candidate = "chatgpt"
		} else if strings.HasPrefix(line, "Logged in using an API key - ") {
			candidate = "api-key"
		}
		if candidate != "" {
			if observed != "" && observed != candidate {
				return errors.New("CODEX_AUTH_UNAVAILABLE")
			}
			observed = candidate
		}
	}
	if observed == "" {
		return errors.New("CODEX_AUTH_UNAVAILABLE")
	}
	if observed != mode {
		return errors.New("CODEX_AUTH_MODE_MISMATCH")
	}
	return nil
}

func (runtime *CodexAppServerRuntime) checkAccount(ctx context.Context) error {
	mode := codexAuthMode(runtime.cfg)
	if mode == "inherit" {
		return nil
	}
	response, err := runtime.request(ctx, "account/read", map[string]any{"refreshToken": false})
	if err != nil {
		return errors.New("CODEX_AUTH_CHECK_FAILED")
	}
	var account struct {
		Account *struct {
			Type string `json:"type"`
		} `json:"account"`
	}
	if json.Unmarshal(response, &account) != nil || account.Account == nil {
		return errors.New("CODEX_AUTH_UNAVAILABLE")
	}
	expected := "chatgpt"
	if mode == "api-key" {
		expected = "apiKey"
	}
	if account.Account.Type != expected {
		return errors.New("CODEX_AUTH_MODE_MISMATCH")
	}
	return nil
}
