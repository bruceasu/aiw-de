package me.asu.aidev.workspace.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Local Workspace configuration in the Web Console. */
@Controller
public class WorkspaceWebController {
    private final ManagedWorkspaceService workspaces;
    private final WebConsoleSecurityService security;

    public WorkspaceWebController(ManagedWorkspaceService workspaces, WebConsoleSecurityService security) {
        this.workspaces = workspaces;
        this.security = security;
    }

    @GetMapping("/ui/workspaces")
    public String list(HttpSession session, Model model) {
        security.requireOperator(session);
        model.addAttribute("page", "workspaces");
        model.addAttribute("workspaces", workspaces.list());
        return "ui/workspaces";
    }

    @GetMapping("/ui/workspaces/{id}")
    public String detail(@PathVariable String id, HttpSession session, Model model) {
        security.requireOperator(session);
        var workspace = workspaces.get(id);
        model.addAttribute("page", "workspaces");
        model.addAttribute("workspace", workspace);
        model.addAttribute("visibleTargets", workspace.targets());
        return "ui/workspace-detail";
    }

    @PostMapping("/ui/workspaces")
    public String create(@RequestParam String id, @RequestParam String displayName,
                         @RequestParam String rootPath,
                         @RequestParam(defaultValue = "false") boolean rootTarget,
                         HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            var workspace = workspaces.create(id, displayName, rootPath, rootTarget);
            flash.addFlashAttribute("successMessage", "Workspace registered: " + workspace.displayName());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces";
    }

    @PostMapping("/ui/workspaces/{id}/repos")
    public String addChild(@PathVariable String id, @RequestParam String repoId,
                           @RequestParam(required = false) String relativePath,
                           @RequestParam(required = false) String kind,
                           @RequestParam(required = false) String source,
                           @RequestParam(defaultValue = "false") boolean initializeGit,
                           HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            var target = kind == null || kind.isBlank()
                    ? workspaces.addChild(id, repoId, relativePath)
                    : workspaces.append(id, repoId, kind, source, initializeGit);
            flash.addFlashAttribute("successMessage", "Directory registered: " + target.repositoryRef());
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/repos/{repoId}/delete")
    public String removeChild(@PathVariable String id, @PathVariable String repoId,
                              @RequestParam(defaultValue = "false") boolean confirmed,
                              HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            if (!confirmed) throw new IllegalArgumentException("Confirm repository deletion before continuing");
            workspaces.removeChild(id, repoId);
            flash.addFlashAttribute("successMessage", "Repository registration removed. Local files were kept.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/name")
    public String rename(@PathVariable String id, @RequestParam String displayName,
                         HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            workspaces.rename(id, displayName);
            flash.addFlashAttribute("successMessage", "Workspace name updated.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/root")
    public String updateRoot(@PathVariable String id, @RequestParam String rootPath,
                             HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            workspaces.updateRoot(id, rootPath);
            flash.addFlashAttribute("successMessage", "Workspace directory updated.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/root-target")
    public String setRootTarget(@PathVariable String id, @RequestParam boolean rootTarget,
                                HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            workspaces.setRootTarget(id, rootTarget);
            flash.addFlashAttribute("successMessage", "Workspace target selection updated.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/enabled")
    public String setEnabled(@PathVariable String id, @RequestParam boolean enabled,
                             HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            workspaces.setEnabled(id, enabled);
            flash.addFlashAttribute("successMessage", enabled ? "Workspace enabled." : "Workspace disabled.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/ui/workspaces/" + id;
    }

    @PostMapping("/ui/workspaces/{id}/delete")
    public String delete(@PathVariable String id, @RequestParam(defaultValue = "false") boolean confirmed,
                         HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try {
            if (!confirmed) throw new IllegalArgumentException("Confirm Workspace deletion before continuing");
            workspaces.delete(id);
            flash.addFlashAttribute("successMessage", "Workspace registration removed. Local files were kept.");
            return "redirect:/ui/workspaces";
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/ui/workspaces/" + id;
        }
    }
}
