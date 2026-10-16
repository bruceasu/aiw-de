package main

import (
    "errors"
    "fmt"
    "regexp"
    "sync/atomic"
    "strings"
)

type AIPreferences struct { Model string `json:"model"`; Effort string `json:"effort"` }
var errAIPreferencesRejected = errors.New("AI_PREFERENCES_REJECTED")
var aiPreferenceValue = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:/-]{0,159}$`)
var aiPreferenceError = regexp.MustCompile(`(?i)(model|reasoning[_ .]effort|effort).{0,100}(not supported|unsupported|not found|does not exist|not available)|(unsupported|invalid).{0,30}(model|reasoning[_ .]effort)`)
func aiPreferencesRejected(value any) bool { return aiPreferenceError.MatchString(fmt.Sprint(value)) }
type aiPreferenceCapture struct { rejected atomic.Bool }
func (c *aiPreferenceCapture) Write(value []byte) (int, error) { n := len(value); if len(value) > 4096 { value = value[:4096] }; if aiPreferencesRejected(string(value)) { c.rejected.Store(true) }; return n, nil }

func stepAISelection(task ClaimedTask, cfg Config, baseline *NamedAIProfile) *NamedAIProfile {
    p := task.AIPreferences
    if p == nil || (p.Model == "" && p.Effort == "") { return baseline }
    if task.AIGuidance != nil {
        for _, item := range task.AIGuidance.AllowedProfiles {
            if item.Profile.Provider == "openai" && item.Profile.Runtime == cfg.AgentRuntime &&
                (cfg.AgentRuntime == "codex-cli" || !item.Profile.Fast) &&
                (p.Model == "" || item.Profile.Model == p.Model) && (p.Effort == "" || item.Profile.Effort == p.Effort) { chosen := item; return &chosen }
        }
        return baseline
    }
    model, effort := cfg.Model, cfg.ReasoningEffort
    if cfg.AgentRuntime == "openai-api" { model, effort = cfg.OpenAIModel, cfg.OpenAIReasoningEffort }
    if aiPreferenceValue.MatchString(p.Model) { model = p.Model }
    efforts := " none minimal low medium high "
    if cfg.AgentRuntime != "openai-api" { efforts += "xhigh " }
    if p.Effort != "" && strings.Contains(efforts, " "+p.Effort+" ") { effort = p.Effort }
    return &NamedAIProfile{Profile: AIProfile{Provider:"openai", Runtime:cfg.AgentRuntime, Model:model, Effort:effort}}
}

func selectedModel(selection *NamedAIProfile, cfg Config) string {
    if selection != nil { return selection.Profile.Model }
    if cfg.AgentRuntime == "openai-api" { return cfg.OpenAIModel }; return cfg.Model
}

func workerEffort(cfg Config) string { if cfg.AgentRuntime == "openai-api" { return cfg.OpenAIReasoningEffort }; return cfg.ReasoningEffort }

func sameAISettings(first, second *NamedAIProfile, cfg Config) bool {
    return selectedModel(first, cfg) == selectedModel(second, cfg) && selectedEffort(first, workerEffort(cfg)) == selectedEffort(second, workerEffort(cfg)) && selectedServiceTier(first) == selectedServiceTier(second)
}
func aiPreferenceReason(task ClaimedTask, cfg Config, selection *NamedAIProfile) string {
    p := task.AIPreferences
    if p == nil || (p.Model == "" && p.Effort == "") { return "Worker choice" }
    if (p.Model != "" && p.Model != selectedModel(selection, cfg)) || (p.Effort != "" && p.Effort != selectedEffort(selection, workerEffort(cfg))) { return "Invalid or unavailable Step preference; use Worker choice for unavailable fields" }
    return "Step preference"
}
