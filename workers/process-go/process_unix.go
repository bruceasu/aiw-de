//go:build !windows

package main

import (
    "os/exec"
    "syscall"
    "time"
)
func configureProcessTree(cmd *exec.Cmd) {
    cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
    cmd.WaitDelay = 3*time.Second
    cmd.Cancel = func() error {
        pid := cmd.Process.Pid
        err := syscall.Kill(-pid, syscall.SIGKILL)
        if err == syscall.ESRCH { return nil }; return err
    }
}
