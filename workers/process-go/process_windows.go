//go:build windows

package main

import (
    "context"
    "os/exec"
    "path/filepath"
    "os"
    "strconv"
    "syscall"
    "time"
)
func configureProcessTree(cmd *exec.Cmd) {
    cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
    cmd.WaitDelay = 3*time.Second
    cmd.Cancel = func() error {
        ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second); defer cancel()
        killer := exec.CommandContext(ctx, filepath.Join(os.Getenv("SystemRoot"), "System32", "taskkill.exe"), "/PID", strconv.Itoa(cmd.Process.Pid), "/T", "/F")
        killer.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
        if err := killer.Run(); err != nil { _ = cmd.Process.Kill(); return err }; return nil
    }
}
