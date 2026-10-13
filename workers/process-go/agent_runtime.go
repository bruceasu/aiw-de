package main

import (
	"context"
	"errors"
)

// AgentRuntime owns one model execution. Worker lifecycle, leases, workspace
// materialization, evidence, and result publication remain outside this seam.
type AgentRuntime interface {
	Type() string
	Capabilities() AgentRuntimeCapabilities
	Run(context.Context, AgentRunRequest) (AgentRunResult, error)
}

type AgentRuntimeCapabilities struct {
	Resume bool
	AttemptCancellation bool
	EventStreaming bool
	Tools bool
}

type AgentRunRequest struct {
	Task        ClaimedTask
	Workspace   string
	ReadOnly    bool
	Instruction string
	Selection   *NamedAIProfile
	Evidence    *AttemptEvidence
	OnSession   func(string)
}

type AgentRunResult struct {
	SessionID    string
	Output       string
	InputTokens  int64
	OutputTokens int64
	UsageKnown   bool
}

func resolveAgentRuntime(cfg Config) (AgentRuntime, error) {
	if err := validateCodexAuthConfig(cfg); err != nil {
		return nil, err
	}
	switch cfg.AgentRuntime {
	case "codex-cli":
		return CodexAgentRuntime{cfg: cfg}, nil
	case "codex-app-server":
		return NewCodexAppServerRuntime(cfg), nil
	case "openai-api":
		if cfg.OpenAIAPIKey == "" {
			return nil, errors.New("OPENAI_API_KEY is required for openai-api")
		}
		return NewOpenAIAgentRuntime(cfg), nil
	default:
		return nil, errors.New("unsupported AIW_AGENT_RUNTIME")
	}
}

type CodexAgentRuntime struct { cfg Config }

func (CodexAgentRuntime) Type() string { return "codex-cli" }

func (runtime CodexAgentRuntime) Start(ctx context.Context) error {
	return checkCodexAuthentication(ctx, runtime.cfg, "")
}

func (CodexAgentRuntime) Close(context.Context) error { return nil }
func (CodexAgentRuntime) Capabilities() AgentRuntimeCapabilities {
	return AgentRuntimeCapabilities{AttemptCancellation: true, EventStreaming: true, Tools: true}
}

type managedAgentRuntime interface {
	Start(context.Context) error
	Close(context.Context) error
}

type failureAwareRuntime interface {
	Failure() <-chan struct{}
	FailureError() error
}

func (runtime CodexAgentRuntime) Run(ctx context.Context, request AgentRunRequest) (AgentRunResult, error) {
	cfg := runtime.cfg
	if err := checkCodexAuthentication(ctx, cfg, request.Workspace); err != nil {
		return AgentRunResult{}, err
	}
	selection := request.Selection
	if selection != nil {
		cfg.Model = selection.Profile.Model
		cfg.ReasoningEffort = selection.Profile.Effort
		cfg.Fast = selection.Profile.Fast
		cfg.AIProfileSelected = true
	}
	sessionID, output, err := runCodex(ctx, cfg, request.Workspace, request.Instruction,
		func(id string) { if request.OnSession != nil { request.OnSession(id) } }, request.Evidence)
	return AgentRunResult{SessionID: sessionID, Output: output}, err
}
