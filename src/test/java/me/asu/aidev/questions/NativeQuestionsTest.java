package me.asu.aidev.questions;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.application.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.run.*;
import me.asu.aidev.work.application.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.*;
import me.asu.aidev.worker.port.WorkerRepository;
import me.asu.aidev.workflow.application.PlanLocations;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static me.asu.aidev.questions.QuestionContract.*;

class NativeQuestionsTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;NativeQuestions nativeQuestions;NativePlanOwners owners;ExecutionGuard guard;
    WorkTaskRepository tasks;WorkTaskService service;FormalQuestions questions;WorkTask original;Question question;
    String config="native-config:"+"c".repeat(64),store="handoff-store:"+"b".repeat(64);

    static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked") ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);return provider;
    }
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("questions.db")));
        jdbc.execute("CREATE TABLE authority_lock(id INTEGER,version INTEGER)");jdbc.update("INSERT INTO authority_lock VALUES(1,0)");
        jdbc.execute("CREATE TABLE formal_question_slot(source_task_id TEXT PRIMARY KEY,question_id TEXT,checkpoint_json TEXT,successor_task_id TEXT)");
        jdbc.execute("CREATE TABLE execution_run_session(attempt_id TEXT,task_id TEXT,configuration_capability TEXT)");
        jdbc.update("INSERT INTO execution_run_session VALUES('attempt','plan~work~work-1',?)",config);
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT,task_id TEXT,ai_profile_id TEXT,ai_profile_version TEXT,ai_runtime TEXT,ai_model TEXT)");
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt','plan~work~work-1','fixed','v1','codex-app-server','model-a')");
        var repo=new AuthorityRepository(jdbc);var boundary=new AuthorityBoundary(repo);
        owners=mock(NativePlanOwners.class);guard=mock(ExecutionGuard.class);tasks=mock(WorkTaskRepository.class);service=mock(WorkTaskService.class);questions=mock(FormalQuestions.class);
        var dependencies=mock(TaskDependencyRepository.class);when(dependencies.findDependencies(any())).thenReturn(List.of());
        original=new WorkTask(new TaskId("plan~work~work-1"),TaskType.IMPLEMENTATION,TaskState.WAITING_INPUT,"flexible-plan","plan",Set.of("original-capability"),"fixed-skill","Original goal",1,null,null,TaskPriority.NORMAL,1,Instant.now(),Instant.now());
        var basis=new NativePlanOwners.Basis("owner","plan",1,"a".repeat(64),original);
        when(owners.task(original.id().value(),false)).thenReturn(basis);when(owners.task(original.id().value(),true)).thenReturn(basis);
        when(tasks.find(any())).thenReturn(Optional.empty());when(tasks.find(original.id())).thenReturn(Optional.of(original));
        when(tasks.transitionState(eq(original.id()),eq(TaskState.WAITING_INPUT),eq(TaskState.CONTINUED),any())).thenReturn(true);
        var reference=new TaskHandoffs.Reference(".ai/handoffs/"+"a".repeat(64)+"/"+"b".repeat(64)+"/manifest.json","d".repeat(64));
        var checkpoint=new Checkpoint(null,null,null,List.of(),"e".repeat(64),store,LOCATION_PROTOCOL,"D:/workspace",reference);
        question=new Question(LOCATION_PROTOCOL,"question","owner",new Source(SourceKind.NATIVE_PLAN_TASK,"plan",1,original.id().value(),null),"attempt","gap",Kind.PROFESSIONAL,"Which target?","",List.of(),"",1,State.ANSWERED,2,new Answer("Use target A","human","CONSOLE","message",2,false,null),checkpoint,null,null,null);
        when(questions.get("question")).thenReturn(question);
        jdbc.update("INSERT INTO formal_question_slot VALUES(?,?,?,NULL)",original.id().value(),"question",repo.encode(checkpoint));
        nativeQuestions=new NativeQuestions(repo,boundary,guard,owners,mock(NativeRunLifecycle.class),tasks,mock(WorkerRepository.class),mock(TaskHandoffs.class),mock(PlanLocations.class),dependencies,provider(questions),provider(service),provider(mock(TaskExecutionService.class)));
    }
    @Test void nonGitCheckpointIsValidButCannotMasqueradeAsLegacyGit() {
        assertThat(question.checkpoint().locationBased()).isTrue();assertThat(question.checkpoint().resultRevision()).isNull();
        assertThatThrownBy(()->new Open(PROTOCOL,"gap",question.source(),new GuardContract.Lease("worker","attempt","lease"),"Which target?","",List.of(),"",question.checkpoint())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exactConfirmedAnswerCreatesOnlyOneSuccessorWithFrozenConfigurationAndSkills() {
        var first=nativeQuestions.apply(question);assertThat(nativeQuestions.apply(question)).isEqualTo(first);
        var command=ArgumentCaptor.forClass(CreateWorkTaskCommand.class);verify(service).createReplacing(command.capture(),eq(original.id()));
        assertThat(command.getValue().requiredCapabilities()).contains(config,store,NativePlanOwners.CAPABILITY,LOCATION_CAPABILITY,"original-capability");
        assertThat(command.getValue().skillRef()).isEqualTo("fixed-skill");
        assertThat(command.getValue().instruction()).startsWith("Original goal").contains("revision 2","Use target A","not new tool authority");
    }
    @Test void missingOriginalConfigurationNeverCreatesAReplacement() {
        jdbc.update("UPDATE execution_run_session SET configuration_capability=NULL");
        assertThatThrownBy(()->nativeQuestions.apply(question)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(service);
    }
    @Test void aNewExecutionPolicyCannotBeBypassedThroughQuestionContinuation() {
        when(guard.required(original)).thenReturn(true);assertThat(nativeQuestions.apply(question)).isNull();verifyNoInteractions(service);
    }
    @Test void aNewTaskIdCannotResetTheOriginalModelChoice() {
        String next=nativeQuestions.apply(question).successorTaskId();
        var applied=new Question(question.protocol(),question.questionId(),question.ownerId(),question.source(),question.sourceAttemptId(),question.sourceQuestionKey(),question.kind(),question.question(),question.context(),question.options(),question.answerRequirements(),question.requestVersion(),State.APPLIED,question.answerRevision(),question.answer(),question.checkpoint(),null,next,null);
        when(questions.get("question")).thenReturn(applied);
        var task=mock(WorkTask.class);when(task.id()).thenReturn(new TaskId(next));when(task.subjectType()).thenReturn("flexible-plan");
        var fixed=new me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile("fixed",new me.asu.aidev.ai.application.AiProfileCatalog.Profile("openai","codex-app-server","model-a","high",false,1));
        var another=new me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile("another",new me.asu.aidev.ai.application.AiProfileCatalog.Profile("openai","codex-app-server","model-b","high",false,2));
        var current=new me.asu.aidev.ai.application.AiProfileCatalog.Guidance("v1",List.of(fixed,another),null,"another");
        assertThat(nativeQuestions.pinGuidance(task,"codex-app-server:coder",current).suggestedProfile()).isEqualTo("fixed");
        assertThatThrownBy(()->nativeQuestions.validateSelection(task,"codex-app-server:coder","another","v1",current)).isInstanceOf(IllegalStateException.class);
        nativeQuestions.validateSelection(task,"codex-app-server:coder","fixed","v1",current);
    }
}
