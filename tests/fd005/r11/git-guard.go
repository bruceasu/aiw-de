// Test-only offline guard: invokes installed Git and retains bounded synthetic diagnostics.
package main

import (
  "crypto/sha256"
  "encoding/hex"
  "encoding/json"
  "fmt"
  "hash"
  "io"
  "os"
  "os/exec"
  "path/filepath"
  "strings"
  "time"
)

var realGit string
var ownedRoot string
var diagnosticRoot string

type capture struct { size int64; kept []byte; limit int; digest hash.Hash }
func (value *capture) Write(data []byte) (int,error) {
  value.size += int64(len(data)); _,_ = value.digest.Write(data)
  remaining := value.limit - len(value.kept)
  if remaining > len(data) { remaining = len(data) }
  if remaining > 0 { value.kept = append(value.kept, data[:remaining]...) }
  return len(data),nil
}
func within(root,target string) bool {
  relative,err := filepath.Rel(root,target)
  return err == nil && relative != ".." && !strings.HasPrefix(relative,".."+string(filepath.Separator)) && !filepath.IsAbs(relative)
}
func appendRecord(record map[string]any) error {
  data,err := json.Marshal(record); if err != nil { return err }
  data = append(data,'\n')
  if len(data) > 64*1024 { return fmt.Errorf("FD005_GIT_DIAGNOSTIC_ROW_LIMIT") }
  lockPath := filepath.Join(diagnosticRoot,"git-invocations.lock")
  deadline := time.Now().Add(200*time.Millisecond)
  var lock *os.File
  for {
    lock,err = os.OpenFile(lockPath,os.O_CREATE|os.O_EXCL|os.O_WRONLY,0600)
    if err == nil { break }
    if !os.IsExist(err) || time.Now().After(deadline) { return fmt.Errorf("FD005_GIT_DIAGNOSTIC_LOCK_FAILED") }
    time.Sleep(2*time.Millisecond)
  }
  defer func(){_ = lock.Close(); _ = os.Remove(lockPath)}()
  filename := filepath.Join(diagnosticRoot,"git-invocations.jsonl")
  if info,statErr := os.Stat(filename); statErr == nil && info.Size()+int64(len(data)) > 2*1024*1024 { return fmt.Errorf("FD005_GIT_DIAGNOSTIC_CAPACITY") }
  file,err := os.OpenFile(filename,os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600)
  if err != nil { return err }; defer file.Close()
  _,err = file.Write(data); return err
}
func fail(code string, exit int) { fmt.Fprintln(os.Stderr,code); os.Exit(exit) }
func main() {
  root,err := filepath.EvalSymlinks(ownedRoot)
  if err != nil || !filepath.IsAbs(root) || !filepath.IsAbs(realGit) { fail("FD005_GIT_GUARD_ROOT_INVALID",90) }
  cwd,err := os.Getwd(); if err != nil { fail("FD005_GIT_GUARD_CWD_INVALID",90) }
  cwd,err = filepath.EvalSymlinks(cwd)
  if err != nil || !within(root,cwd) { fail("FD005_GIT_GUARD_FOREIGN_CWD",90) }
  expected := filepath.Join(filepath.Dir(filepath.Dir(root)),"evidence-r11","results","diagnostics")
  expected,err = filepath.EvalSymlinks(expected)
  if err != nil { fail("FD005_GIT_GUARD_DIAGNOSTIC_ROOT_INVALID",90) }
  actual,err := filepath.EvalSymlinks(diagnosticRoot)
  if err != nil || !strings.EqualFold(actual,expected) { fail("FD005_GIT_GUARD_FOREIGN_DIAGNOSTIC_ROOT",90) }
  diagnosticRoot = actual
  argv := os.Args[1:]
  if len(argv) > 64 || len(strings.Join(argv,"\x00")) > 16*1024 { fail("FD005_GIT_GUARD_ARGV_LIMIT",90) }
  invocationID := fmt.Sprintf("synthetic-git-%d",os.Getpid())
  begin := time.Now()
  if err := appendRecord(map[string]any{"syntheticTestOnly":true,"invocationId":invocationID,"phase":"invoke","cwd":cwd,"argv":argv,"executable":realGit}); err != nil { fail("FD005_GIT_DIAGNOSTIC_WRITE_FAILED",91) }
  environment := []string{}
  for _,value := range os.Environ() {
    name := strings.ToUpper(strings.SplitN(value,"=",2)[0])
    if name == "HOME" || name == "USERPROFILE" || name == "XDG_CONFIG_HOME" || name == "SSH_AUTH_SOCK" || name == "GITHUB_TOKEN" || strings.HasPrefix(name,"GIT_") || strings.HasPrefix(name,"AIW_") || strings.HasPrefix(name,"OPENAI_") || strings.HasPrefix(name,"OTEL_") { continue }
    environment = append(environment,value)
  }
  home := filepath.Join(root,"home")
  environment = append(environment,"HOME="+home,"USERPROFILE="+home,"XDG_CONFIG_HOME="+filepath.Join(home,"xdg"),
    "GIT_CONFIG_NOSYSTEM=1","GIT_CONFIG_GLOBAL="+filepath.Join(home,"offline-git-config"),
    "GIT_TERMINAL_PROMPT=0","GIT_ALLOW_PROTOCOL=file","GIT_OPTIONAL_LOCKS=0")
  command := exec.Command(realGit,argv...); command.Env = environment; command.Dir = cwd; command.Stdin = os.Stdin
  stdout := &capture{limit:0,digest:sha256.New()}; stderr := &capture{limit:16*1024,digest:sha256.New()}
  command.Stdout = io.MultiWriter(os.Stdout,stdout); command.Stderr = io.MultiWriter(os.Stderr,stderr)
  runErr := command.Run(); exitCode := 0; launchFailure := false
  if runErr != nil {
    if failure,ok := runErr.(*exec.ExitError); ok { exitCode = failure.ExitCode() } else { exitCode = 90; launchFailure = true }
  }
  if err := appendRecord(map[string]any{"syntheticTestOnly":true,"invocationId":invocationID,"phase":"exit","cwd":cwd,"argv":argv,
    "exitCode":exitCode,"launchFailure":launchFailure,"durationMs":time.Since(begin).Milliseconds(),
    "stdoutBytes":stdout.size,"stdoutDigest":hex.EncodeToString(stdout.digest.Sum(nil)),
    "stderrBytes":stderr.size,"stderr":string(stderr.kept),"stderrDigest":hex.EncodeToString(stderr.digest.Sum(nil)),
    "stderrTruncated":int64(len(stderr.kept)) < stderr.size}); err != nil { fail("FD005_GIT_DIAGNOSTIC_WRITE_FAILED",91) }
  if launchFailure { fmt.Fprintln(os.Stderr,"FD005_GIT_LAUNCH_FAILURE") }
  os.Exit(exitCode)
}
