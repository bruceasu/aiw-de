package me.asu.aidev.execution.config;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.authority.AuthorityGrantService;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryTaskProfilesTest {
    @TempDir Path temp;

    @Test void renamingAndDeletingProfilesPreservesExistingTaskSnapshots() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("catalog.db")));
        jdbc.execute("CREATE TABLE authority_audit(id TEXT, target_key TEXT, grant_id TEXT, action_id TEXT, actor TEXT, event TEXT, detail TEXT, created_ms INTEGER)");
        jdbc.execute("CREATE TABLE worker_team_member(state TEXT, settings_json TEXT)");
        jdbc.execute("CREATE TABLE workflow_definition(spec_json TEXT)");
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT, state TEXT, run_json TEXT)");
        jdbc.execute("CREATE TABLE worker_team_assignment(run_id TEXT, profile_json TEXT)");
        jdbc.execute("CREATE TABLE workflow_schedule(state TEXT, spec_json TEXT)");
        jdbc.execute("CREATE TABLE workflow_task_runtime(task_id TEXT PRIMARY KEY, profile_json TEXT)");
        var repo=new AuthorityRepository(jdbc);
        var catalogue=mock(AiProfileCatalog.class);
        when(catalogue.approved(TaskType.EXTERNAL_WORK)).thenReturn(List.of());
        var factory=new AgentFactorySettings(repo,mock(AuthorityBoundary.class),mock(AuthorityGrantService.class),catalogue);
        var settings=profile("fast_text",1,"openai","responses","fast-model",null,null).settings();
        factory.saveProfile(new AgentFactorySettings.ProfileChange("initial","fast_text",settings,true,true,true));
        var original=factory.runtimeProfiles();
        var tasks=new FactoryTaskProfiles(factory,repo);
        var existing=task("already-dispatched");
        tasks.bind(existing.id().value(),tasks.resolve("fast_text"));

        factory.renameProfile(new AgentFactorySettings.ProfileRename(original.revision(),"fast_text","Quick response"));
        var renamed=factory.runtimeProfiles();
        assertThat(renamed.items().getFirst().label()).isEqualTo("Quick response");
        assertThat(renamed.items().getFirst().version()).isEqualTo(1);
        assertThat(renamed.defaultId()).isEqualTo("fast_text");
        assertThat(tasks.read(existing)).isEqualTo(original.items().getFirst());
        assertThatThrownBy(()->factory.deleteProfile(new AgentFactorySettings.ProfileDelete(original.revision(),"fast_text")))
                .isInstanceOf(IllegalStateException.class);

        var delete=new AgentFactorySettings.ProfileDelete(renamed.revision(),"fast_text");
        jdbc.update("INSERT INTO workflow_definition VALUES(?)","{\"nodes\":[{\"runtimeProfileCandidates\":[\"fast_text\"]}]}");
        assertThatThrownBy(()->factory.deleteProfile(delete))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("saved workflow");
        jdbc.update("DELETE FROM workflow_definition");
        jdbc.update("INSERT INTO workflow_execution VALUES(?,?,?)","old","COMPLETED","{\"runtimeProfile\":\"fast_text\"}");
        factory.deleteProfile(delete);
        var empty=factory.runtimeProfiles();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.defaultId()).isNull();
        assertThat(empty.managerDefaultId()).isNull();
        assertThat(tasks.read(existing)).isEqualTo(original.items().getFirst());

        factory.saveProfile(new AgentFactorySettings.ProfileChange(empty.revision(),"fast_text",settings,true,true,true));
        assertThat(tasks.resolve("fast_text").version()).isEqualTo(2);
    }

    @Test void nodesPinTheirOwnProfilesAndContinuationRetainsOriginalConnection() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("profiles.db")));
        jdbc.execute("CREATE TABLE workflow_task_runtime(task_id TEXT PRIMARY KEY,profile_json TEXT NOT NULL)");
        var factory=mock(AgentFactorySettings.class);
        var quick=profile("quick",1,"openai","responses","fast-model","MC-10000000-0000-0000-0000-000000000001",1L);
        var deep=profile("deep",2,"deepseek","chat-completions","deep-model",null,null);
        when(factory.runtimeProfiles()).thenReturn(new AgentFactorySettings.RuntimeProfiles("a","quick","quick",List.of(quick,deep)));
        var profiles=new FactoryTaskProfiles(factory,new AuthorityRepository(jdbc));
        var first=task("first");
        var second=task("second");
        var resumed=task("first-resume-1");
        profiles.bind(first.id().value(),profiles.resolve(null));
        profiles.bind(second.id().value(),profiles.resolve("deep"));
        profiles.inherit(first.id().value(),resumed.id().value());
        when(factory.runtimeProfiles()).thenReturn(new AgentFactorySettings.RuntimeProfiles("b","deep","deep",List.of(deep)));

        assertThat(profiles.read(first)).isEqualTo(quick);
        assertThat(profiles.read(resumed)).isEqualTo(quick);
        assertThat(profiles.read(second)).isEqualTo(deep);
        assertThat(profiles.canClaim(first,worker("openai-agents:task"))).isTrue();
        assertThat(profiles.canClaim(first,worker("openai-agents:deepseek:chat-completions:task"))).isFalse();
        assertThat(profiles.canClaim(second,worker("openai-agents:deepseek:chat-completions:task"))).isTrue();
        assertThat(profiles.select(quick,"quick","1","fixed").model()).isEqualTo("fast-model");
        assertThatThrownBy(()->profiles.select(quick,"deep","2","changed")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->profiles.resolve("quick")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void overlappingWritersWaitForStopEvidenceWhileAnIndependentDirectoryRemainsAvailable() throws Exception {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("workspaces.db")));
        jdbc.execute("CREATE TABLE workflow_task_runtime(task_id TEXT PRIMARY KEY,profile_json TEXT NOT NULL,canonical_location TEXT)");
        jdbc.execute("CREATE TABLE plan_execution_location(scope_id TEXT PRIMARY KEY,location TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_type TEXT,subject_id TEXT,skill_ref TEXT,lease_token TEXT)");
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,state TEXT)");
        jdbc.execute("CREATE TABLE execution_stop_evidence(attempt_id TEXT PRIMARY KEY)");
        Path root=Files.createDirectory(temp.resolve("root"));
        Path nested=Files.createDirectory(root.resolve("nested"));
        Path independent=Files.createDirectory(temp.resolve("independent"));
        var profiles=new FactoryTaskProfiles(mock(AgentFactorySettings.class),new AuthorityRepository(jdbc));
        var authority=mock(AuthorityBoundary.class);
        ReflectionTestUtils.setField(profiles,"boundary",authority);
        var fixed=profile("local",1,"openai","responses","model",null,null);
        var owner=writer("owner");
        var overlap=writer("overlap");
        var separate=writer("separate");
        for(var entry:java.util.Map.of(owner,root,overlap,nested,separate,independent).entrySet()){
            var task=entry.getKey();
            profiles.bind(task.id().value(),fixed);
            jdbc.update("INSERT INTO plan_execution_location VALUES(?,?)",task.subjectId(),entry.getValue().toString());
            jdbc.update("INSERT INTO work_task VALUES(?,?,?,?,NULL)",task.id().value(),task.subjectType(),task.subjectId(),task.skillRef());
        }
        assertThat(profiles.workspaceAvailable(owner,true)).isTrue();
        assertThat(profiles.canonicalLocation(owner)).isEqualTo(root.toRealPath().toString());
        verify(authority).lock();
        jdbc.update("UPDATE work_task SET lease_token='lease' WHERE id='owner'");
        assertThat(profiles.workspaceAvailable(overlap,false)).isFalse();
        assertThat(profiles.workspaceAvailable(separate,false)).isTrue();
        jdbc.update("UPDATE work_task SET lease_token=NULL WHERE id='owner'");
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt','owner','EXPIRED')");
        assertThat(profiles.workspaceAvailable(overlap,false)).isFalse();
        jdbc.update("INSERT INTO execution_stop_evidence VALUES('attempt')");
        assertThat(profiles.workspaceAvailable(overlap,false)).isTrue();
    }

    private WorkTask writer(String id) {
        return new WorkTask(new TaskId(id),TaskType.EXTERNAL_WORK,TaskState.READY,"flexible-plan","plan-"+id,
                Set.of(),"workspace-work","Edit project",null,null,new TaskPriority(0),0,Instant.EPOCH,Instant.EPOCH);
    }

    private AgentFactorySettings.RuntimeProfile profile(String id,long version,String provider,String protocol,String model,String ref,Long revision) {
        return new AgentFactorySettings.RuntimeProfile(id,version,true,
                new AgentFactorySettings.Settings("openai-agents",model,"high",true,"python","","",ref,revision,provider,protocol));
    }

    private WorkTask task(String id) {
        var now=Instant.now();
        return new WorkTask(new TaskId(id),TaskType.EXTERNAL_WORK,TaskState.READY,"flexible-plan","same-plan",
                Set.of(),"general-work","Review the document",null,null,new TaskPriority(0),0,now,now);
    }

    private Worker worker(String runtime) {
        var now=Instant.now();
        return new Worker(new WorkerId("worker"),runtime,Set.of("workflow-runtime-profile-v1"),1,0,
                WorkerStatus.ACTIVE,now,now,now);
    }
}
