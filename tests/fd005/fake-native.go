// synthetic-test-only: this program is never a Codex executable or capability proof.
package main

import (
  "bufio"
  "crypto/sha256"
  "encoding/hex"
  "encoding/json"
  "fmt"
  "io"
  "os"
  "strings"
)

func metadata(value any) {
  name := os.Getenv("FD005_NATIVE_MARKER")
  if name == "" { return }
  file, err := os.OpenFile(name, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
  if err != nil { return }
  defer file.Close()
  _ = json.NewEncoder(file).Encode(value)
}
func output(value any) { _ = json.NewEncoder(os.Stdout).Encode(value) }
func main() {
  metadata(map[string]any{"syntheticTestOnly": true, "started": true})
  args := os.Args[1:]
  appServer := false
  for _, arg := range args { if arg == "app-server" { appServer = true } }
  if !appServer {
    body, _ := io.ReadAll(io.LimitReader(os.Stdin, 2<<20))
    digest := sha256.Sum256(body)
    leaked := false
    for _, entry := range os.Environ() {
      key := strings.SplitN(entry, "=", 2)[0]
      if strings.HasPrefix(key, "AIW_") || strings.HasPrefix(key, "OTEL_") || key == "DEBUG" { leaked = true }
    }
    fmt.Fprint(os.Stderr, "SYNTHETIC_STDERR_BODY_NEVER_LOG\n")
    output(map[string]any{"syntheticTestOnly": true, "args": args, "stdinBytes": len(body), "stdinSha256": hex.EncodeToString(digest[:]), "leakedEnvironment": leaked, "modelAuthAvailable": os.Getenv("OPENAI_API_KEY") == "SYNTHETIC_MODEL_TOKEN", "rustLog": os.Getenv("RUST_LOG")})
    return
  }
  scanner := bufio.NewScanner(os.Stdin)
  scanner.Buffer(make([]byte, 4096), 2<<20)
  for scanner.Scan() {
    var message map[string]any
    if json.Unmarshal(scanner.Bytes(), &message) != nil { return }
    method, _ := message["method"].(string)
    metadata(map[string]any{"syntheticTestOnly": true, "method": method})
    if method == "" {
      metadata(map[string]any{"syntheticTestOnly": true, "clientReplyForwarded": true})
      continue
    }
    id := message["id"]
    if id == nil { continue }
    switch method {
    case "initialize": output(map[string]any{"id": id, "result": map[string]any{"userAgent": "fd005-synthetic-only"}})
    case "thread/start", "thread/read":
      ephemeral := true
      var diskPath any = nil
      if os.Getenv("FD005_RPC_BAD_RECEIPT") == "true" || (method == "thread/read" && os.Getenv("FD005_RPC_BAD_READ") == "true") { ephemeral = false; diskPath = "SYNTHETIC_FORBIDDEN_SESSION_PATH" }
      output(map[string]any{"id": id, "result": map[string]any{"thread": map[string]any{"id": "synthetic-thread", "ephemeral": ephemeral, "path": diskPath}, "model": "synthetic-model"}})
      if os.Getenv("FD005_RPC_EMIT_APPROVAL") == "true" && method == "thread/start" {
        output(map[string]any{"id": 9001, "method": "item/commandExecution/requestApproval", "params": map[string]any{"threadId": "synthetic-thread", "turnId": "synthetic-turn", "itemId": "synthetic-command"}})
      }
    case "turn/start": output(map[string]any{"id": id, "result": map[string]any{"turn": map[string]any{"id": "synthetic-turn", "status": "completed", "items": []any{}}}})
    default: output(map[string]any{"id": id, "result": map[string]any{"syntheticTestOnly": true}})
    }
  }
}
