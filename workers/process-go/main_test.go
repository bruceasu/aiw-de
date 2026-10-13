package main

import (
	"path/filepath"
	"testing"
)

func TestTomlProfiles(t *testing.T) {
	paths, err := filepath.Glob(filepath.Join("profiles", "*.toml"))
	if err != nil || len(paths) != 10 {
		t.Fatalf("expected 10 executable Go TOML profiles, got %d: %v", len(paths), err)
	}
	for _, path := range paths {
		profile := loadRoleProfile(path)
		if profile.Name == "" || profile.RoleCapability == "" || len(profile.AllowedTaskTypes) == 0 {
			t.Fatalf("incomplete profile: %s", path)
		}
	}
}

func TestTaskTypeAllowed(t *testing.T) {
	p := RoleProfile{AllowedTaskTypes: []string{"EXTERNAL_WORK"}}
	if !taskTypeAllowed(p, "EXTERNAL_WORK") {
		t.Fatal("expected EXTERNAL_WORK to be allowed")
	}
	if taskTypeAllowed(p, "IMPLEMENTATION") {
		t.Fatal("did not expect IMPLEMENTATION to be allowed")
	}
}

func TestBuildRoleInstructionPreservesAuthorityBoundary(t *testing.T) {
	p := RoleProfile{Name: "software-requirements-analyst", ProtocolSkills: []string{"protocol"}, PrimarySkill: "primary", Instruction: "Analyze requirements.", ReadOnly: true}
	task := ClaimedTask{TaskType: "EXTERNAL_WORK", SkillRef: "primary", Instruction: "User requirement here"}
	got := buildRoleInstruction(Config{Profile: p, SkillsRoot: "testdata/skills", IncludeReferences: false}, task)
	for _, want := range []string{
		"software-requirements-analyst",
		"Control Plane owns lifecycle state",
		"This role is read-only",
		"User requirement here",
	} {
		if !contains(got, want) {
			t.Fatalf("instruction missing %q: %s", want, got)
		}
	}
}

func contains(s, sub string) bool {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
