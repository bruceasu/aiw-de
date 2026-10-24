package me.asu.aidev.execution.guard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deployment-owned accounting mode. Requests cannot select or change it. */
@Component
public final class ExecutionAccounting {
    private final String mode;

    public ExecutionAccounting(@Value("${aidev.execution.accounting-mode:record-only}") String mode) {
        if (!"record-only".equals(mode) && !"enforce".equals(mode))
            throw new IllegalArgumentException("Execution accounting mode must be record-only or enforce");
        this.mode = mode;
    }

    public String mode() { return mode; }
    public boolean enforced() { return mode.equals("enforce"); }
}
