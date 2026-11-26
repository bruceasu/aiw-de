package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import me.asu.aidev.execution.config.ManagerProfileSelection;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.application.WorkflowRuns;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WorkflowConsole {
    private final WorkflowRuns workflows;
    private final WebConsoleSecurityService security;
    @org.springframework.beans.factory.annotation.Autowired
    private me.asu.aidev.execution.skills.SkillFiles platformSkills;
    @org.springframework.beans.factory.annotation.Autowired
    private me.asu.aidev.execution.config.AgentFactorySettings factory;
    @org.springframework.beans.factory.annotation.Autowired
    private ManagerProfileSelection manager;
    public WorkflowConsole(WorkflowRuns workflows,WebConsoleSecurityService security) {this.workflows=workflows;this.security=security;}
    public record Save(String id,long version,WorkflowRuns.Spec spec) {}
    public record Start(String requestId,String definitionId,long version,String input,String location) {}
    public record Simple(String requestId,String instruction,String location,String memberId,WorkflowRuns.Assignment assignment,java.util.List<String> requiredSkills,java.util.List<WorkflowRuns.Material> sources,boolean workspaceTools,java.util.List<me.asu.aidev.execution.guard.GuardContract.Command> commands,boolean autonomousShell,String acceptance,List<String> requiredCheckIds,String runtimeProfile,List<me.asu.aidev.execution.tools.SharedToolAccess.Ref> sharedTools) {}
    public record Control(long version,String action,String answer) {}
    public record Supplement(String requestId,String text,String mode) {}
    public record Amendment(long version,String requestId,String node,List<String> nodes,String change,String mode) {
        public Amendment(long version,String requestId,String node,List<String> nodes,String change) { this(version,requestId,node,nodes,change,null); }
        public Amendment(long version,String requestId,String node,String change) { this(version,requestId,node,null,change,null); }
    }
    public record AmendmentDecision(long version,String proposalId,boolean apply) {}
    @PostMapping("/api/r1/workflows/executions/{id}/amendments") @ResponseBody public Object propose(@PathVariable String id,@RequestBody Amendment input,HttpSession session) {
        security.requireOperator(session);
        requireSharedToolAdmin(id,session);
        if("STRUCTURE".equals(input.mode()))return workflows.proposeStructure(id,input.version(),input.requestId(),input.change());
        if(input.mode()!=null&&!input.mode().isBlank()&&!"CONTENT".equals(input.mode()))throw new IllegalArgumentException("Unknown amendment mode");
        var nodes=input.nodes();
        if(nodes!=null&&!nodes.isEmpty())return workflows.propose(id,input.version(),input.requestId(),nodes,input.change());
        return input.node()==null?workflows.propose(id,input.version(),input.requestId(),List.of(),input.change())
                :workflows.propose(id,input.version(),input.requestId(),input.node(),input.change());
    }
    @PostMapping("/api/r1/workflows/executions/{id}/amendments/decision") @ResponseBody public Object decide(@PathVariable String id,@RequestBody AmendmentDecision input,HttpSession session) {
        requireSharedToolAdmin(id,session);
        security.requireOperator(session);return workflows.decideProposal(id,input.version(),input.proposalId(),input.apply());
    }
    @GetMapping("/ui/workflows") public String page(Model model) {return page(model,"workflows");}
    @GetMapping("/api/r1/workflows/runtime-profiles") @ResponseBody public Object runtimeProfiles(HttpSession session) {
        security.requireOperator(session);
        return factory.runtimeProfiles().items().stream().filter(p->p.enabled()).map(p->Map.of(
                "id",p.id(),"displayName",p.label(),"version",p.version(),"model",p.settings().model(),"runtime",p.settings().runtime())).toList();
    }
    @GetMapping("/ui/skills") public String skillsPage() {return "ui/platform-skills";}
    @GetMapping("/api/r1/workflows/skills") @ResponseBody public Object skills(HttpSession session) {
        security.requireOperator(session);
        return platformSkills.catalogue(security.actor(session));
    }
    @GetMapping("/api/r1/workflows/skills/builtin") @ResponseBody public Object builtinSkills(HttpSession session) {
        security.requireOperator(session);
        return platformSkills.builtinCatalogue();
    }
    @GetMapping("/api/r1/workflows/skills/{ref}/versions") @ResponseBody public Object skillVersions(HttpSession session,@PathVariable String ref) {
        security.requireOperator(session);
        return platformSkills.versions(security.actor(session),ref);
    }
    @PostMapping("/api/r1/workflows/skills") @ResponseBody public Object saveSkill(HttpSession session,
            @RequestBody me.asu.aidev.execution.skills.SkillFiles.CatalogueEdit edit) {
        security.requireOperator(session);
        return platformSkills.saveCatalogue(security.actor(session),edit);
    }
    @PostMapping("/api/r1/workflows/skills/manager-review") @ResponseBody public Object reviewSkill(HttpSession session,
            @RequestBody ManagerProfileSelection.SkillDraft draft) {
        security.requireOperator(session);
        return manager.reviewSkill(security.actor(session),draft);
    }
    @GetMapping("/ui/tasks") public String tasks(@RequestParam(required=false) String execution, Model model,HttpSession session) {
        return execution==null||execution.isBlank()?taskPage(model,"list",null):taskDetail(execution,model,session);
    }
    @GetMapping("/ui/tasks/new") public String newTask(Model model) {return taskPage(model,"new",null);}
    @GetMapping("/ui/tasks/{id}") public String taskDetail(@PathVariable String id,Model model,HttpSession session) {
        security.requireOperator(session);
        workflows.requireVisibleExecution(id,security.actor(session));
        return taskPage(model,"detail",id);
    }
    private String taskPage(Model model,String taskPage,String execution) {
        model.addAttribute("taskPage",taskPage);
        model.addAttribute("taskExecution",execution);
        return page(model,"tasks");
    }
    private String page(Model model,String mode) {
        model.addAttribute("page",mode);model.addAttribute("workflowMode",mode);
        model.addAttribute("workflowTitle",mode.equals("tasks")?"Tasks":"Workflows");
        model.addAttribute("workflowSubtitle",mode.equals("tasks")?"Describe the work and follow its execution.":"Edit a reusable workflow and follow each execution.");
        return "ui/workflows";
    }
    @GetMapping("/api/r1/workflows") @ResponseBody public Object list(HttpSession session,@RequestParam(defaultValue="all") String kind) {
        security.requireOperator(session);return Map.of("definitions",workflows.definitions(),"executions",workflows.executions(kind));
    }
    @GetMapping("/api/r1/workflows/caretaker-alerts") @ResponseBody public Object caretakerAlerts(HttpSession session) {
        security.requireOperator(session);return workflows.caretakerAlerts();
    }
    @PostMapping("/api/r1/workflows/definitions") @ResponseBody public Object save(@RequestBody Save input,HttpSession session) {
        security.requireOperator(session);
        if(input.spec()!=null&&input.spec().nodes().stream().anyMatch(n->!n.sharedTools().isEmpty()))security.requireAdmin(session);
        return workflows.save(input.id(),input.version(),input.spec());
    }
    @GetMapping("/api/r1/workflows/definitions/{id}") @ResponseBody public Object definition(@PathVariable String id,HttpSession session) {
        security.requireOperator(session);return workflows.definition(id);
    }
    @PostMapping("/api/r1/workflows/start") @ResponseBody public Object start(@RequestBody Start input,HttpSession session) {
        security.requireOperator(session);
        if(!security.role(session).canAdmin()&&workflows.definition(input.definitionId()).spec().nodes().stream().anyMatch(n->!n.sharedTools().isEmpty()))
            security.requireAdmin(session);
        return workflows.start(input.requestId(),input.definitionId(),input.version(),input.input(),input.location());
    }
    @PostMapping("/api/r1/workflows/tasks") @ResponseBody public Object simple(@RequestBody Simple input,HttpSession session) {
        security.requireOperator(session);
        if(input.sharedTools()!=null&&!input.sharedTools().isEmpty())security.requireAdmin(session);
        return workflows.simple(input.requestId(),input.instruction(),input.location(),input.memberId(),input.assignment(),input.requiredSkills(),input.sources(),input.workspaceTools(),input.commands(),input.autonomousShell(),input.acceptance(),input.requiredCheckIds(),input.runtimeProfile(),input.sharedTools());
    }
    @GetMapping("/api/r1/workflows/tasks/history") @ResponseBody
    public WorkflowRuns.TaskHistory taskHistory(HttpSession session,@RequestParam(defaultValue="current") String view,
            @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page) {
        security.requireOperator(session);
        return workflows.taskHistory(view,state,q,page);
    }
    @PostMapping("/api/r1/workflows/tasks/{id}/history") @ResponseBody
    public Map<String,String> manageTask(@PathVariable String id,@RequestBody Map<String,String> input,HttpSession session) {
        String action=input.getOrDefault("action","");
        if(action.equals("archive"))security.requireOperator(session);
        else if(Set.of("schedule-delete","undo-delete").contains(action))security.requireAdmin(session);
        else throw new IllegalArgumentException("Unknown task history action");
        workflows.manageFinishedTask(id,action);
        return Map.of("action",action);
    }
    @GetMapping("/api/r1/workflows/executions/{id}") @ResponseBody public Object view(@PathVariable String id,HttpSession session) {
        security.requireOperator(session);return workflows.view(id);
    }
    @PostMapping("/api/r1/workflows/executions/{id}/control") @ResponseBody public Object control(@PathVariable String id,@RequestBody Control input,HttpSession session) {
        if("retry".equals(input.action()))requireSharedToolAdmin(id,session);
        security.requireOperator(session);return workflows.control(id,input.version(),input.action(),input.answer());
    }
    private void requireSharedToolAdmin(String id,HttpSession session) {
        if(!security.role(session).canAdmin()&&workflows.hasSharedTools(id,security.actor(session)))security.requireAdmin(session);
    }
    @PostMapping("/api/r1/workflows/executions/{id}/inputs") @ResponseBody public Object supplement(@PathVariable String id,@RequestBody Supplement input,HttpSession session) {
        security.requireOperator(session);return workflows.supplement(id,input.requestId(),input.text(),input.mode());
    }
    @ExceptionHandler(SecurityException.class) @ResponseBody public ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(Map.of("error","This workflow belongs to another user or needs a current login."));
    }
    @ExceptionHandler(NoSuchElementException.class) @ResponseBody public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error","Workflow or execution not found."));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.unprocessableContent().body(Map.of("error",Objects.toString(error.getMessage(),"Invalid workflow input")));
    }
    @ExceptionHandler(IllegalStateException.class) @ResponseBody public ResponseEntity<?> changed(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("error",Objects.toString(error.getMessage(),"Workflow state changed")));
    }
}
