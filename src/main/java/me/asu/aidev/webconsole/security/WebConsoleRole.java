package me.asu.aidev.webconsole.security;

public enum WebConsoleRole {
    VIEWER,
    OPERATOR,
    APPROVER,
    ADMIN;

    public boolean canOperate() {
        return this == OPERATOR || this == ADMIN;
    }

    public boolean canApprove() {
        return this == APPROVER || this == ADMIN;
    }

    public boolean canAdmin() {
        return this == ADMIN;
    }
}
