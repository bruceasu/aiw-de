package me.asu.aidev.webconsole.security;

public enum WebConsoleRole {
    ANONYMOUS,
    ADMIN;

    public boolean canOperate() {
        return this == ADMIN;
    }

    public boolean canApprove() {
        return this == ADMIN;
    }

    public boolean canAdmin() {
        return this == ADMIN;
    }
}
