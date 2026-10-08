package me.asu.aidev.webconsole.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.application.WebConsoleActionService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/ui")
public class WebConsoleActionController {
    private final WebConsoleActionService actions;
    private final WebConsoleSecurityService security;

    public WebConsoleActionController(WebConsoleActionService actions, WebConsoleSecurityService security) {
        this.actions = actions;
        this.security = security;
    }

    @PostMapping("/runs")
    public String startRun(@RequestParam(required = false) String requestId,
                           @RequestParam String requirement,
                           @RequestParam String repositoryRef,
                           @RequestParam(required = false) String baseRevision,
                           @RequestParam(required = false) String capabilities,
                           @RequestParam(defaultValue = "NORMAL") String priority,
                           @RequestParam(defaultValue = "0") Integer maxParallelism,
                           HttpSession session,
                           RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            var run = actions.startRun(requestId, requirement, repositoryRef, baseRevision, capabilities, priority, maxParallelism);
            flash.addFlashAttribute("successMessage", "Autonomous run created: " + run.runId());
            return redirectRun(run.runId());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", message(e));
            return "redirect:/ui/runs/new";
        }
    }

    @PostMapping("/runs/{runId}/pause")
    public String pause(@PathVariable String runId, @RequestParam(required = false) String reason,
                        HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Run paused.", () -> actions.pause(runId, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/resume")
    public String resume(@PathVariable String runId, @RequestParam(required = false) String reason,
                         HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Run resumed.", () -> actions.resume(runId, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/recover")
    public String recover(@PathVariable String runId, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Failed tasks requeued and run recovered.",
                () -> actions.recover(runId, security.actor(session)));
    }

    @PostMapping("/runs/{runId}/cancel")
    public String cancel(@PathVariable String runId, @RequestParam String reason,
                         HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Run cancelled.",
                () -> actions.cancel(runId, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/scheduling")
    public String runScheduling(@PathVariable String runId, @RequestParam int maxParallelism,
                                @RequestParam(required = false) String reason,
                                HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Run scheduling limit updated.",
                () -> actions.updateRunScheduling(runId, maxParallelism, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/tasks/{taskId}/instruction")
    public String reviseTaskInstruction(@PathVariable String runId, @PathVariable String taskId,
                                        @RequestParam long expectedVersion, @RequestParam int expectedRevision,
                                        @RequestParam String instruction, @RequestParam String reason,
                                        HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Task instruction revised.",
                () -> actions.reviseTaskInstruction(runId, taskId, expectedVersion, expectedRevision,
                        instruction, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/tasks/{taskId}/stop")
    public String stopTask(@PathVariable String runId, @PathVariable String taskId,
                           @RequestParam String reason, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Worker stop requested; task remains active until acknowledged.",
                () -> actions.stopTask(runId, taskId, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/tasks/{taskId}/force-requeue")
    public String forceRequeueTask(@PathVariable String runId, @PathVariable String taskId,
                                   @RequestParam String reason,
                                   @RequestParam(defaultValue = "false") boolean warningConfirmed,
                                   HttpSession session, RedirectAttributes flash) {
        security.requireAdmin(session);
        return execute(flash, redirectRun(runId), "Task force-requeued; the old Worker may still cause external side effects.",
                () -> actions.forceRequeueTask(runId, taskId, security.actor(session), reason, warningConfirmed));
    }

    @PostMapping("/runs/{runId}/tasks/{taskId}/correction")
    public String createTaskCorrection(@PathVariable String runId, @PathVariable String taskId,
                                       @RequestParam String instruction, @RequestParam String reason,
                                       HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, redirectRun(runId), "Linked correction task created.",
                () -> actions.createCorrectionTask(runId, taskId, instruction, security.actor(session), reason));
    }

    @PostMapping("/runs/{runId}/tasks/{taskId}/workspace-replacement")
    public String replaceTaskWorkspace(@PathVariable String runId, @PathVariable String taskId,
                                       @RequestParam long expectedVersion, @RequestParam String repositoryRef,
                                       @RequestParam String mode,
                                       @RequestParam(required = false) String revision,
                                       @RequestParam String reason,
                                       HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        String actor = security.actor(session);
        try {
            actions.replaceTaskWorkspace(runId, taskId, expectedVersion, repositoryRef,
                    mode, revision, actor, reason);
            flash.addFlashAttribute("successMessage", "Workspace replacement request recorded.");
        } catch (RuntimeException failure) {
            actions.recordRejectedWorkspaceReplacement(runId, taskId, repositoryRef,
                    revision, actor, reason, message(failure));
            flash.addFlashAttribute("errorMessage", message(failure));
        }
        return redirectRun(runId);
    }

    @PostMapping("/runs/{runId}/approval")
    public String requestApproval(@PathVariable String runId, HttpSession session, RedirectAttributes flash) {
        security.requireApprover(session);
        return execute(flash, redirectRun(runId), "Approval request created.",
                () -> actions.requestApproval(runId, security.actor(session)));
    }

    @PostMapping("/approvals/{approvalRequestId}/approve")
    public String approve(@PathVariable String approvalRequestId, @RequestParam(required = false) String note,
                          @RequestParam(required = false) String runId,
                          HttpSession session, RedirectAttributes flash) {
        security.requireApprover(session);
        return execute(flash, approvalRedirect(runId), "Approval recorded.",
                () -> actions.approve(approvalRequestId, security.actor(session), note));
    }

    @PostMapping("/approvals/{approvalRequestId}/reject")
    public String reject(@PathVariable String approvalRequestId, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String runId,
                         HttpSession session, RedirectAttributes flash) {
        security.requireApprover(session);
        return execute(flash, approvalRedirect(runId), "Rejection recorded.",
                () -> actions.reject(approvalRequestId, security.actor(session), note));
    }

    @PostMapping("/workers/{workerId}/drain")
    public String drainWorker(@PathVariable String workerId, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/workers", "Worker is draining and will receive no new work.",
                () -> actions.drainWorker(workerId));
    }

    @PostMapping("/workers/{workerId}/offline")
    public String offlineWorker(@PathVariable String workerId, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/workers", "Worker marked offline.",
                () -> actions.offlineWorker(workerId));
    }

    @PostMapping("/system/fleet-scheduling")
    public String fleetScheduling(@RequestParam int maxParallelism,
                                  @RequestParam(required = false) String reason,
                                  HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/system", "Fleet scheduling limit updated.",
                () -> actions.updateFleetScheduling(maxParallelism, security.actor(session), reason));
    }

    private static String execute(RedirectAttributes flash, String redirect, String success, Runnable operation) {
        try {
            operation.run();
            flash.addFlashAttribute("successMessage", success);
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", message(e));
        }
        return redirect;
    }

    private static String redirectRun(String runId) {
        return "redirect:/ui/runs/" + UriUtils.encodePathSegment(runId, StandardCharsets.UTF_8);
    }

    private static String approvalRedirect(String runId) {
        return runId == null || runId.isBlank() ? "redirect:/ui/approvals" : redirectRun(runId);
    }

    private static String message(RuntimeException e) {
        String value = e.getMessage();
        return value == null || value.isBlank() ? e.getClass().getSimpleName() : value;
    }
}
