package me.asu.aidev.webconsole.protocol.http;

import me.asu.aidev.webconsole.application.WebConsoleQueryService;
import me.asu.aidev.webconsole.application.WebConsoleMissionQueryService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import me.asu.aidev.work.application.TaskWorkspaceReplacementService;
import jakarta.servlet.http.HttpSession;
import java.util.NoSuchElementException;
import me.asu.aidev.webconsole.domain.WorkerIdentity;
import me.asu.aidev.fleet.domain.FleetDashboard;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** R9 Web Console query controller. Mutation handlers live in WebConsoleActionController. */
@Controller
@RequestMapping
public class WebConsoleController {
    private final WebConsoleQueryService console;
    private final WebConsoleSecurityService security;
    private final WebConsoleMissionQueryService missions;
    private ManagedWorkspaceService managedWorkspaces;
    private TaskWorkspaceReplacementService workspaceReplacements;

    @Autowired
    public void setManagedWorkspaces(ManagedWorkspaceService managedWorkspaces) {
        this.managedWorkspaces = managedWorkspaces;
    }

    @Autowired
    public void setWorkspaceReplacements(TaskWorkspaceReplacementService workspaceReplacements) {
        this.workspaceReplacements = workspaceReplacements;
    }

    public WebConsoleController(WebConsoleQueryService console, WebConsoleSecurityService security,
                                WebConsoleMissionQueryService missions) {
        this.console = console;
        this.security = security;
        this.missions = missions;
    }

    @GetMapping("/")
    public String root() { return "redirect:/ui/plans"; }

    @GetMapping("/ui")
    public String dashboard(Model model) {
        return "redirect:/ui/plans";
    }

    @GetMapping("/ui/runs")
    public String runs(@RequestParam(defaultValue = "current") String view, Model model) {
        return "redirect:/ui/plans";
    }

    @GetMapping("/ui/runs/new")
    public String newRun(HttpSession session, Model model) {
        security.requireOperator(session);
        return "redirect:/ui/plans/new";
    }

    @GetMapping("/ui/runs/{runId}")
    public String run(@PathVariable String runId, HttpSession session, Model model) {
        return "redirect:/ui/plans";
    }

    @GetMapping("/ui/workers")
    public String workers(Model model) {
        model.addAttribute("page", "workers");
        var fleet = console.fleet();
        model.addAttribute("fleet", fleet);
        model.addAttribute("fleetDetail", console.fleetDetail(fleet));
        model.addAttribute("workerIdentities", identities(fleet.workers().workers()));
        return "ui/workers";
    }

    private static Map<String, WorkerIdentity> identities(List<FleetDashboard.WorkerSummary> workers) {
        return workers.stream().collect(Collectors.toMap(FleetDashboard.WorkerSummary::workerId,
                worker -> WorkerIdentity.forId(worker.workerId())));
    }

    @GetMapping("/ui/approvals")
    public String approvals(Model model) {
        return "redirect:/ui/plans";
    }

    @GetMapping("/ui/system")
    public String system(Model model) {
        model.addAttribute("page", "system");
        model.addAttribute("fleet", console.fleet());
        model.addAttribute("fleetSchedulingEvents", console.fleetSchedulingEvents(50));
        return "ui/system";
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    public ModelAndView notFound(NoSuchElementException error) {
        ModelAndView view = new ModelAndView("ui/not-found");
        view.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        view.addObject("message", error.getMessage());
        view.addObject("page", "plans");
        return view;
    }
}
