package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalPlanSelectionTest {
    @TempDir Path temp;
    JdbcTemplate db;
    PersonalTaskPreparations service;
    AgentFactory factory;
    AuthorityRepository repo;
    final AuthorityBoundary.Principal principal=new AuthorityBoundary.Principal("owner","secretary",1,true);
    @BeforeEach void setup() {
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("test.db")));
        db.execute("CREATE TABLE authority_managed_plan(plan_id TEXT,owner_id TEXT,worker_id TEXT)");
        db.execute("INSERT INTO authority_managed_plan VALUES('old','owner','secretary')");
        db.execute("CREATE TABLE flexible_workflow_plan(id TEXT,state TEXT)");
        db.execute("INSERT INTO flexible_workflow_plan VALUES('old','COMPLETED')");
        db.execute("CREATE TABLE execution_preparation(owner_id TEXT,intent_id TEXT,intent_version INTEGER)");
        repo=new AuthorityRepository(db);
        var options=mock(ConfigurationUiOptions.class);
        when(options.snapshot("owner")).thenReturn(Map.of("plans",List.of(new ConfigurationUiOptions.PlanChoice(
                "old","Old work",1,false,false,Long.MAX_VALUE,"/ui/plans/old",List.of()))));
        factory=mock(AgentFactory.class);
        service=new PersonalTaskPreparations(repo,mock(AuthorityBoundary.class),mock(ActionAdmissionService.class),
                mock(ManagedPlanAdmission.class),options,mock(ExecutionConfigurations.class),factory,null,null,null,null);
    }
    @Test void completedPlanIsRejectedBeforeFactoryOrTaskCreation() {
        var input=new PersonalTaskPreparations.Input(UUID.randomUUID().toString(),"old","repo","a".repeat(40),"coder","model","Add robot");
        assertThatThrownBy(() -> service.prepare(principal,input)).isInstanceOf(PersonalTaskPreparations.PlanNotDraft.class);
        verifyNoInteractions(factory);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_preparation",Integer.class)).isZero();
    }
    @Test void conflictResponseIdentifiesPlanStateWithoutLeakingExceptionDetails() {
        var controller=new PersonalTaskPreparationController(repo,service);
        var response=controller.planNotDraft(new PersonalTaskPreparations.PlanNotDraft("old","COMPLETED"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(((Map<?,?>)response.getBody()).get("code")).isEqualTo("PROFESSIONAL_PLAN_NOT_DRAFT");
        assertThat(((Map<?,?>)response.getBody()).get("planState")).isEqualTo("COMPLETED");
    }
}
