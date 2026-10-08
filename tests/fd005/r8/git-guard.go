// Test-only offline guard. It invokes installed Git; it does not simulate Git results.
package main

import (
  "fmt"
  "os"
  "os/exec"
  "path/filepath"
  "strings"
)

var realGit string
var ownedRoot string

func main() {
  root, err := filepath.EvalSymlinks(ownedRoot)
  if err != nil || !filepath.IsAbs(root) || !filepath.IsAbs(realGit) { os.Exit(90) }
  cwd, err := os.Getwd()
  if err != nil { os.Exit(90) }
  cwd, err = filepath.EvalSymlinks(cwd)
  if err != nil { os.Exit(90) }
  relative, err := filepath.Rel(root, cwd)
  if err != nil || relative == ".." || strings.HasPrefix(relative, ".." + string(filepath.Separator)) || filepath.IsAbs(relative) { os.Exit(90) }
  environment := []string{}
  for _, value := range os.Environ() {
    name := strings.ToUpper(strings.SplitN(value, "=", 2)[0])
    if name == "HOME" || name == "USERPROFILE" || name == "XDG_CONFIG_HOME" || name == "SSH_AUTH_SOCK" || name == "GITHUB_TOKEN" || strings.HasPrefix(name, "GIT_") || strings.HasPrefix(name, "AIW_") || strings.HasPrefix(name, "OPENAI_") || strings.HasPrefix(name, "OTEL_") { continue }
    environment = append(environment, value)
  }
  home := filepath.Join(root, "home")
  environment = append(environment, "HOME=" + home, "USERPROFILE=" + home, "XDG_CONFIG_HOME=" + filepath.Join(home, "xdg"),
    "GIT_CONFIG_NOSYSTEM=1", "GIT_CONFIG_GLOBAL=" + filepath.Join(home, "offline-git-config"), "GIT_TERMINAL_PROMPT=0", "GIT_ALLOW_PROTOCOL=file", "GIT_OPTIONAL_LOCKS=0")
  command := exec.Command(realGit, os.Args[1:]...)
  command.Env = environment
  command.Dir = cwd
  command.Stdin = os.Stdin; command.Stdout = os.Stdout; command.Stderr = os.Stderr
  if err := command.Run(); err != nil {
    if failure, ok := err.(*exec.ExitError); ok { os.Exit(failure.ExitCode()) }
    fmt.Fprintln(os.Stderr, "SYNTHETIC_OFFLINE_GIT_LAUNCH_FAILURE"); os.Exit(90)
  }
}
