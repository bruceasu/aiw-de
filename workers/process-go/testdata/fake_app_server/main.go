package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

type message struct {
	ID     json.RawMessage `json:"id"`
	Method string          `json:"method"`
	Params struct {
		ThreadID string `json:"threadId"`
		Input    []struct {
			Text string `json:"text"`
		} `json:"input"`
	} `json:"params"`
}

func main() {
	if len(os.Args) == 2 && os.Args[1] == "--version" {
		fmt.Println("codex-cli 0.163.0")
		return
	}
	if len(os.Args) < 2 || os.Args[1] != "app-server" {
		os.Exit(2)
	}
	threads := map[string]string{}
	ignoreInterrupt := map[string]bool{}
	threadNumber, turnNumber := 0, 0
	encoder := json.NewEncoder(os.Stdout)
	scanner := bufio.NewScanner(os.Stdin)
	for scanner.Scan() {
		var request message
		if json.Unmarshal(scanner.Bytes(), &request) != nil {
			os.Exit(3)
		}
		switch request.Method {
		case "initialize":
			_ = encoder.Encode(map[string]any{"jsonrpc": "2.0", "id": request.ID, "result": map[string]any{}})
		case "thread/start":
			threadNumber++
			threadID := fmt.Sprintf("thread-%d", threadNumber)
			_ = encoder.Encode(map[string]any{"jsonrpc": "2.0", "id": request.ID, "result": map[string]any{"thread": map[string]string{"id": threadID}}})
		case "turn/start":
			turnNumber++
			turnID := fmt.Sprintf("turn-%d", turnNumber)
			text := ""
			if len(request.Params.Input) > 0 {
				text = request.Params.Input[0].Text
			}
			threads[request.Params.ThreadID] = turnID
			ignoreInterrupt[request.Params.ThreadID] = strings.Contains(text, "ignore-interrupt")
			_ = encoder.Encode(map[string]any{"jsonrpc": "2.0", "id": request.ID, "result": map[string]any{"turn": map[string]string{"id": turnID}}})
			if strings.Contains(text, "cancel") {
				continue
			}
			writeNotification(encoder, request.Params.ThreadID, turnID, "item/agentMessage/delta", map[string]any{"delta": "reply:" + text})
			writeNotification(encoder, request.Params.ThreadID, turnID, "turn/completed", map[string]any{"turn": map[string]any{
				"id": turnID, "status": "completed", "items": []any{map[string]any{"type": "agentMessage", "text": "reply:" + text}},
			}})
		case "turn/interrupt":
			if ignoreInterrupt[request.Params.ThreadID] {
				continue
			}
			turnID := threads[request.Params.ThreadID]
			_ = encoder.Encode(map[string]any{"jsonrpc": "2.0", "id": request.ID, "result": map[string]any{}})
			writeNotification(encoder, request.Params.ThreadID, turnID, "turn/completed", map[string]any{"turn": map[string]string{"id": turnID, "status": "interrupted"}})
		}
	}
}

func writeNotification(encoder *json.Encoder, threadID, turnID, method string, values map[string]any) {
	params := map[string]any{"threadId": threadID, "turnId": turnID}
	for key, value := range values {
		params[key] = value
	}
	_ = encoder.Encode(map[string]any{"jsonrpc": "2.0", "method": method, "params": params})
}
