package me.asu.aidev.workflow.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository.Control;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowTaskReadRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/** The editor's trusted, executable subset of the general workflow-plan API. */
@Service
public class WorkflowEditorService {
    public record Role(String capability, String skillRef, String resultKind, boolean needsWorkspace) {}
    public record Template(String id, String title, String use, WorkflowPlanSpec plan) {}
    public record StepDetail(WorkTask task, List<String> dependencies, String report, String verdict, String stepKey) {
        public StepDetail(WorkTask task,List<String> dependencies,String report,String verdict) {
            this(task,dependencies,report,verdict,null);
        }
    }
    public record Detail(WorkflowPlan plan, List<StepDetail> steps, List<Control> controls,
                         JdbcWorkflowHumanOutcomeRepository.Outcome humanOutcome,
                         WorkflowPlanService.ReviewRecovery reviewRecovery,
                         List<me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository.Authorization> repairAuthorizations,
                         List<String> decisionWorkers, java.util.Map<String, String> repairDecisionReports,
                         me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository.History configuredExecution,
                         String displayState, FactoryPlanLifecycle.View factoryPlan) {}
    public record TaskDetail(WorkTask task, List<JdbcWorkflowTaskReadRepository.Attempt> attempts,
                             WorkflowPlanService.RetryEligibility retry,
                             List<JdbcWorkflowTaskReadRepository.RetryEvent> retries) {}

    private static final List<Role> ROLES = List.of(
            new Role("requirement-analysis", "software-requirements-analyst", "REPORT", false),
            new Role("solution-architecture", "solution-architect", "REPORT", false),
            new Role("implementation-planning", "implementation-planner", "REPORT", false),
            new Role("coding", "coding-work-package-executor", "WORKSPACE", true),
            new Role("integration", "coding-work-package-executor", "WORKSPACE", true),
            new Role("repair", "coding-work-package-executor", "WORKSPACE", true));
    private static final Set<String> ROLE_CAPABILITIES = Set.of("requirement-analysis", "solution-architecture",
            "implementation-planning", "coding", "integration", "repair", "code-review", "verification",
            "integration-verification", "workflow-review", "workflow-recovery-decision");

    private final WorkflowPlanService plans;
    private final TaskDependencyRepository dependencies;
    private final ExecutionAttemptRepository attempts;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;
    private final JdbcWorkflowTaskReadRepository taskReads;
    private final WorkflowRepairAuthorizationService repairs;
    private me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository configuredHistory;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<FactoryPlanLifecycle> factoryPlans;

    @org.springframework.beans.factory.annotation.Autowired
    public void setConfiguredHistory(me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository history) {
        this.configuredHistory = history;
    }

    public WorkflowEditorService(WorkflowPlanService plans, TaskDependencyRepository dependencies,
                                 ExecutionAttemptRepository attempts, JdbcWorkflowHumanOutcomeRepository outcomes,
                                 JdbcWorkflowTaskReadRepository taskReads, WorkflowRepairAuthorizationService repairs) {
        this.plans = plans;
        this.dependencies = dependencies;
        this.attempts = attempts;
        this.outcomes = outcomes;
        this.taskReads = taskReads;
        this.repairs = repairs;
    }

    public List<Role> roles() { return ROLES; }

    public List<Template> templates() {
        return List.of(
                new Template("software-development", "Core development", "Requirements → architecture → plan → code and tests; each step gets an independent stage review",
                        plan(List.of(
                                step("requirements", "requirement-analysis", "software-requirements-analyst", Set.of(),
                                        "Clarify the goal, scope, constraints and acceptance criteria. Return a reviewable requirements report."),
                                step("architecture", "solution-architecture", "solution-architect", Set.of("requirements"),
                                        "Design a bounded solution from the accepted requirements. Explain interfaces, data changes and risks."),
                                step("implementation-plan", "implementation-planning", "implementation-planner", Set.of("architecture"),
                                        "Turn the accepted requirements and architecture into an implementable plan with checks for completion."),
                                step("implementation", "coding", "coding-work-package-executor", Set.of("implementation-plan"),
                                        "Implement the accepted plan in the selected workspace. Add or update focused tests where needed, run checks only when authorized, and report the exact local result revision. Do not push.")))),
                new Template("analysis", "One step analysis", "Understand a request without creating code work",
                        plan(List.of(step("analysis", "requirement-analysis", "software-requirements-analyst", Set.of(),
                                "Analyze the stated goal and return a bounded report.")))),
                new Template("plan", "Analysis and plan", "Clarify, design and plan before choosing execution",
                        plan(List.of(
                                step("analysis", "requirement-analysis", "software-requirements-analyst", Set.of(), "Analyze the stated goal."),
                                step("architecture", "solution-architecture", "solution-architect", Set.of("analysis"), "Design a bounded solution from the upstream report."),
                                step("plan", "implementation-planning", "implementation-planner", Set.of("architecture"), "Plan independently reviewable work from the upstream reports.")))),
                new Template("delivery", "Code with independent review", "Requires a workspace; Git revision is optional",
                        plan(List.of(
                                step("code", "coding", "coding-work-package-executor", Set.of(), "Implement the approved change in the selected workspace.")))));
    }

    private static WorkflowPlanSpec plan(List<WorkflowPlanSpec.Step> steps) {
        return new WorkflowPlanSpec("Replace with your goal", WorkflowPlanSpec.Source.MANAGER, steps);
    }

    private static WorkflowPlanSpec.Step step(String key, String capability, String skill,
                                               Set<String> dependsOn, String instruction) {
        return new WorkflowPlanSpec.Step(key, Set.of(capability), Set.of(), skill, instruction, null, null, dependsOn);
    }

    public void validate(WorkflowPlanSpec spec) {
        validateSpec(spec, false);
    }

    public void validateCandidate(WorkflowPlanSpec spec) {
        validateSpec(spec, true);
    }

    static void validateSpec(WorkflowPlanSpec spec, boolean candidate) {
        if (spec == null) throw new IllegalArgumentException("plan is required");
        for (WorkflowPlanSpec.Step step : spec.steps()) {
            Role role = ROLES.stream().filter(r -> r.skillRef().equals(step.skillRef())
                    && step.requiredSkills().contains(r.capability())).filter(r -> step.requiredSkills().stream()
                    .noneMatch(skill -> ROLE_CAPABILITIES.contains(skill) && !skill.equals(r.capability()))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(step.key() + ": unsupported role, skillRef or capabilities"));
            if (!candidate && role.needsWorkspace() && step.repositoryRef() == null) {
                throw new IllegalArgumentException(step.key() + ": workspace is required for this role");
            }
        }
    }

    public WorkflowPlan draft(WorkflowPlanSpec spec, String actor) {
        validate(spec);
        return plans.draft(spec, actor);
    }

    public WorkflowPlan confirm(String id, long version, String actor) {
        validate(plans.get(id).spec());
        return plans.confirm(id, version, actor);
    }

    @Transactional(readOnly = true)
    public TaskDetail taskDetail(String id, String taskId) {
        plans.get(id);
        var nativeTask = plans.steps(id).stream().filter(row -> row.id().value().equals(taskId)).findFirst();
        if (nativeTask.isEmpty() && factoryPlans != null) {
            var bound = factoryPlans.getObject().currentTasks(id).stream().filter(row -> row.task().id().value().equals(taskId)).findFirst();
            if (bound.isPresent()) return new TaskDetail(bound.get().task(),taskReads.attempts(taskId),
                    new WorkflowPlanService.RetryEligibility(plans.get(id).version(),false,"Continue or retry through the original factory preparation."),taskReads.retries(taskId));
        }
        WorkTask task = nativeTask.orElseThrow(() -> new WorkflowPlanService.PlanNotFound(taskId));
        return new TaskDetail(task, taskReads.attempts(taskId), plans.retryEligibility(id, taskId),
                taskReads.retries(taskId));
    }

    public Detail detail(String id) {
        WorkflowPlan plan = plans.get(id);
        List<StepDetail> rows = new ArrayList<>();
        for (WorkTask task : plans.steps(id)) {
            List<String> deps = dependencies.findDependencies(task.id()).stream().map(TaskId::value).toList();
            var report = attempts.findCompletionReport(task.id());
            rows.add(new StepDetail(task, deps, report.map(r -> r.summary()).orElse(null),
                    report.map(r -> r.verdict().name()).orElse(null)));
        }
        var configured = configuredHistory == null ? null : configuredHistory.read(id);
        var factoryPlan = factoryPlans == null ? null : factoryPlans.getObject().view(id);
        if (factoryPlan != null) for (var bound : factoryPlans.getObject().currentTasks(id)) {
            var report = attempts.findCompletionReport(bound.task().id());
            rows.add(new StepDetail(bound.task(),dependencies.findDependencies(bound.task().id()).stream().map(TaskId::value).toList(),
                    report.map(r -> r.summary()).orElse(null),report.map(r -> r.verdict().name()).orElse(null),bound.stepKey()));
        }
        String displayState = plan.state()==WorkflowPlan.State.COMPLETED ? "COMPLETED" : (factoryPlan!=null || rows.isEmpty()) && configured != null
                ? me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository.displayState(configured.state()) : null;
        return new Detail(plan, rows, plans.controlHistory(id), outcomes.find(id).orElse(null), plans.reviewRecovery(id),
                repairs.list(id), repairs.decisionWorkers(id), repairs.decisionReports(id),
                configured, displayState, factoryPlan);
    }
}
