package me.asu.aidev.webconsole.protocol.http;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "me.asu.aidev")
public class WorkflowConsoleModelAdvice {
    @ModelAttribute("flexibleWorkflowEnabled")
    public boolean flexibleWorkflowEnabled() { return true; }
}
