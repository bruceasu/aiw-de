package me.asu.aidev.worker.application;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.authority.AuthorityTaskPolicy;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.workflow.application.WorkflowRuns;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.workflow.application.WorkflowRuns.Material;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamMemberSettingsTest {
    @Test void wishTaskUsesItsFrozenMemberAndShowsCurrentMemberState() {
        var connection=new org.springframework.jdbc.datasource.SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        try {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(connection);
            jdbc.execute("CREATE TABLE worker_team_assignment(run_id TEXT,node_key TEXT,member_id TEXT,profile_json TEXT)");
            jdbc.execute("CREATE TABLE worker_team_task(task_id TEXT,run_id TEXT,node_key TEXT)");
            jdbc.execute("CREATE TABLE worker_team_member(id TEXT,state TEXT)");
            jdbc.execute("CREATE TABLE work_task(id TEXT,subject_type TEXT)");
            jdbc.execute("CREATE TABLE goal_request(id TEXT,frozen_json TEXT)");
            var db=new AuthorityRepository(jdbc);
            var frozen=new TeamMembers.Profile("member",2,"Original analyst","Clarify",List.of("goal-discussion"),"","ACTIVE",null);
            jdbc.update("INSERT INTO work_task VALUES('task','goal-discussion'),('ordinary','workflow')");
            jdbc.update("INSERT INTO worker_team_member VALUES('member','DRAINING')");
            jdbc.update("INSERT INTO goal_request VALUES('task',?)",db.encode(Map.of("member",frozen)));
            var members=new TeamMembers(db,null,null);
            assertThat(members.forTask("task").revision()).isEqualTo(2);
            assertThat(members.identity("task")).containsEntry("name","Original analyst").containsEntry("state","DRAINING");
            assertThat(members.forTask("ordinary")).isNull();
        } finally {connection.destroy();}
    }
    private AiProfileCatalog.NamedProfile model(String id,String name) {
        return new AiProfileCatalog.NamedProfile(id,new AiProfileCatalog.Profile("openai","openai-agents",name,"low",false,1));
    }
    @Test void frozenSourcesKeepTheirContentAndProvenanceOutsideInstructions() {
        var material=new Material("knowledge","System guide","Confirmed system facts",null);
        var original=new ArrayList<>(List.of(material));
        var settings=new TeamMembers.Settings(null,null,null,original,List.of(new TeamMembers.Experience("source-run","Review assumptions")));
        var member=new TeamMembers.Profile("member-test",1,"Reviewer","Review changes",List.of(),"A human note","ACTIVE",settings);
        original.clear();var members=new TeamMembers(null,null,null);
        assertThat(members.materials(member,List.of())).hasSize(3);
        assertThat(members.materials(member,List.of()).getLast().content()).contains("source-run","Review assumptions");
        assertThat(members.context(member)).doesNotContain("Confirmed system facts","Review assumptions","A human note");
    }
    @Test void lessonRecallRanksRelevantOlderWorkAndKeepsOwnerAndSourceRevision() {
        var connection=new org.springframework.jdbc.datasource.SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        try {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(connection);
            jdbc.execute("CREATE TABLE worker_team_member(id TEXT,actor TEXT)");
            jdbc.execute("CREATE TABLE worker_team_experience(task_id TEXT,actor TEXT,member_id TEXT,run_id TEXT,experience TEXT,created_ms INTEGER)");
            jdbc.execute("CREATE TABLE workflow_execution(id TEXT,actor TEXT,run_json TEXT)");
            jdbc.execute("CREATE TABLE worker_team_task(task_id TEXT,run_id TEXT,node_key TEXT)");
            jdbc.execute("CREATE TABLE worker_team_assignment(run_id TEXT,node_key TEXT,member_id TEXT,profile_json TEXT)");
            jdbc.update("INSERT INTO worker_team_member VALUES('member','owner')");
            var db=new AuthorityRepository(jdbc);
            var member=new TeamMembers.Profile("member",3,"Frozen name","Work",List.of(),"","ACTIVE",null);
            var skillProfile=new TeamMembers.Profile("member",4,"Skill holder","Work",List.of("news-skill"),"","ACTIVE",null);
            for(var id:List.of("project","skill","keyword","unrelated","other-owner")){
                String actor=id.equals("other-owner")?"other":"owner";
                String location=Set.of("project","other-owner").contains(id)?"news-project":"other-project";
                jdbc.update("INSERT INTO workflow_execution VALUES(?,?,?)",id,actor,db.encode(Map.of("location",location)));
                jdbc.update("INSERT INTO worker_team_task VALUES(?,?,'step')",id,id);
                jdbc.update("INSERT INTO worker_team_assignment VALUES(?,'step','member',?)",id,db.encode(id.equals("skill")?skillProfile:member));
                jdbc.update("INSERT INTO worker_team_experience VALUES(?,?,'member',?,?,?)",id,actor,id,
                        id.equals("keyword")?"A JSON export lesson":"A precise data lesson",id.equals("project")?1:10);
            }
            var members=new TeamMembers(db,null,null);
            var lessons=members.relevantLessons(member,"news-project","JSON export",List.of("news-skill"));
            assertThat(lessons).containsExactly("project","skill","keyword");
            assertThat(members.materials(member,List.of("project","other-owner")).getLast().content())
                    .contains("Source task: project","Frozen name (profile revision 3)").doesNotContain("other-owner");
        } finally {connection.destroy();}
    }
    @Test void aFixedDefaultDoesNotTurnIntoAnAutomaticSwitchingRange() {
        var db=mock(AuthorityRepository.class);
        var members=spy(new TeamMembers(db,null,null));var task=mock(WorkTask.class);when(task.id()).thenReturn(new TaskId("task-member-test"));
        var settings=new TeamMembers.Settings("model-a","low",null,null,null);
        doReturn(new TeamMembers.Profile("member-test",1,"Member","Work",List.of(),"","ACTIVE",settings)).when(members).forTask("task-member-test");
        var first=model("first","model-a");var second=model("second","model-b");
        var normal=new AiProfileCatalog.Guidance("v1",List.of(first,second),null,"second");
        assertThat(members.guidance(task,normal,false).allowedProfiles()).containsExactly(first);
        assertThat(members.guidance(task,normal,false).version()).isEqualTo("v1");
        assertThat(members.guidance(task,normal,true)).isSameAs(normal);
    }
    @Test void anAllowedRangeFreezesValuesRatherThanOnlyMutableProfileNames() {
        var db=mock(AuthorityRepository.class);
        var members=spy(new TeamMembers(db,null,null));var task=mock(WorkTask.class);when(task.id()).thenReturn(new TaskId("task-member-test"));
        var saved=model("registered","original-model");
        var settings=new TeamMembers.Settings(null,null,List.of(saved),null,null);
        doReturn(new TeamMembers.Profile("member-test",1,"Member","Work",List.of(),"","ACTIVE",settings)).when(members).forTask("task-member-test");
        var changed=new AiProfileCatalog.Guidance("v2",List.of(model("registered","replacement-model")),null,"registered");
        assertThatThrownBy(()->members.guidance(task,changed,false)).isInstanceOf(IllegalStateException.class);
    }
    @Test void actualStartRejectsModelsOutsideTheFrozenMemberRange() {
        var members=spy(new TeamMembers(mock(AuthorityRepository.class),null,null));
        var settings=new TeamMembers.Settings("model-a","low",null,null,null);
        var task=mock(WorkTask.class);when(task.id()).thenReturn(new TaskId("task-member-test"));when(task.type()).thenReturn(TaskType.EXTERNAL_WORK);
        doReturn(new TeamMembers.Profile("member-test",1,"Member","Work",List.of(),"","ACTIVE",settings)).when(members).forTask("task-member-test");
        var catalog=mock(AiProfileCatalog.class);when(catalog.hasPolicy(TaskType.EXTERNAL_WORK)).thenReturn(true);when(catalog.version()).thenReturn("v1");
        when(catalog.approved(TaskType.EXTERNAL_WORK)).thenReturn(List.of(model("first","model-a"),model("second","model-b")));
        var policy=spy(new AuthorityTaskPolicy(null,null,null,null,null,catalog));doReturn(false).when(policy).managed(task);
        org.springframework.test.util.ReflectionTestUtils.setField(policy,"members",members);
        policy.selection(task,"first","v1");
        assertThatThrownBy(()->policy.selection(task,"second","v1")).isInstanceOf(IllegalStateException.class);
    }
    @Test void evidenceRequiresARealFinishedTaskNotJustAnAssignedFutureNode() {
        var connection=new org.springframework.jdbc.datasource.SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        try {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(connection);
            jdbc.execute("CREATE TABLE workflow_execution(id TEXT,actor TEXT,run_json TEXT)");
            jdbc.execute("CREATE TABLE worker_team_assignment(run_id TEXT,node_key TEXT,member_id TEXT)");
            jdbc.execute("CREATE TABLE worker_team_task(task_id TEXT,run_id TEXT,node_key TEXT)");
            jdbc.execute("CREATE TABLE work_task(id TEXT,state TEXT)");
            jdbc.update("INSERT INTO workflow_execution VALUES('run','actor','source')");
            jdbc.update("INSERT INTO worker_team_assignment VALUES('run','actual','member'),('run','future','member')");
            jdbc.update("INSERT INTO worker_team_task VALUES('task','run','actual')");
            jdbc.update("INSERT INTO work_task VALUES('task','COMPLETED')");
            var run=new WorkflowRuns.Run();run.state="FAILED";
            run.events=List.of(new WorkflowRuns.Event(1,"actual","RESULT","Recorded result"),new WorkflowRuns.Event(2,"future","FAILED","Stopped before dispatch"));
            var db=mock(AuthorityRepository.class);when(db.jdbc()).thenReturn(jdbc);when(db.decode("source",WorkflowRuns.Run.class)).thenReturn(run);
            var members=spy(new TeamMembers(db,null,null));
            doReturn(new TeamMembers.Profile("member",1,"Member","Work",List.of(),"","ACTIVE",null)).when(members).get("actor","member");
            assertThat(members.evidence("actor","member","run").get("content")).isEqualTo("Recorded result");
            jdbc.update("UPDATE work_task SET state='READY'");
            assertThatThrownBy(()->members.evidence("actor","member","run")).isInstanceOf(IllegalArgumentException.class);
            jdbc.update("DELETE FROM worker_team_task");
            assertThatThrownBy(()->members.evidence("actor","member","run")).isInstanceOf(IllegalArgumentException.class);
        } finally {connection.destroy();}
    }
}
