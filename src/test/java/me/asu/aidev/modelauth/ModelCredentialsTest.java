package me.asu.aidev.modelauth;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import static me.asu.aidev.modelauth.ModelAuthContract.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelCredentialsTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;AuthorityRepository repo;AuthorityBoundary boundary;ModelConnections service;OpenAiOAuth oauth;ExecutionGuard guard;
    final String actor="web:admin";
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("credentials.db"));jdbc=new JdbcTemplate(data);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V30__model_credentials.sql"),new ClassPathResource("db/migration/V31__model_worker_proofs.sql")).execute(data);
        jdbc.execute("CREATE TABLE authority_audit(id TEXT,target_key TEXT,grant_id TEXT,request_id TEXT,actor TEXT,event TEXT,detail TEXT,created_ms BIGINT)");
        jdbc.execute("CREATE TABLE execution_context(task_id TEXT,owner_id TEXT)");jdbc.execute("CREATE TABLE authority_owner(id TEXT,actor TEXT)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT,created_by TEXT)");jdbc.update("INSERT INTO flexible_workflow_plan VALUES('plan',?)",actor);
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT,ai_model TEXT)");jdbc.update("INSERT INTO execution_attempt VALUES('attempt','model-a')");
        repo=new AuthorityRepository(jdbc);boundary=mock(AuthorityBoundary.class);when(boundary.human()).thenReturn(actor);
        oauth=mock(OpenAiOAuth.class);guard=mock(ExecutionGuard.class);
        var vault=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]));
        service=spy(new ModelConnections(repo,boundary,mock(AuthorityGrantService.class),vault,oauth,guard,new DataSourceTransactionManager(data)));
        ReflectionTestUtils.setField(service,"workerToken","test-machine-credential");
    }
    Issued issued(String token,long expiry) {
        return new Issued(new TokenSet("access-"+token,"refresh-"+token,"identity-"+token,"openid resource.invoke chatgpt.tokens.use.direct"),"oaiapp_test","subject",null,null,expiry,0,true);
    }
    Connection create(long expiry) {return service.save(actor,Kind.CHATGPT_RESPONSES,"My connection",issued("original",expiry),null);}
    Worker worker(Connection c) {
        var worker=mock(Worker.class);when(worker.id()).thenReturn(new WorkerId("worker"));when(worker.runtimeType()).thenReturn("openai-agents:coder");
        when(worker.capabilities()).thenReturn(Set.of(ModelConnections.capability(c.id(),c.version())));return worker;
    }
    Access input(Connection c) {return new Access(new GuardContract.Lease("worker","attempt","lease"),c.id(),c.version(),"model-a",null);}
    void register(Connection c) {String proof=service.registerWorker("worker",c.id(),c.version(),"openai-agents","model-a");doReturn(proof).when(service).proofHeader();}
    void bind(Connection c) {register(c);jdbc.update("INSERT INTO model_auth_task VALUES('task',?,?,?,'openai-agents','model-a')",c.id(),c.version(),actor);}
    @Test void vaultRequiresKeyAndAuthenticatesOwnerAndGeneration() {
        var vault=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]));String cipher=vault.seal("owner:1","secret");
        assertThat(cipher).doesNotContain("secret");assertThat(vault.open("owner:1",cipher)).isEqualTo("secret");
        assertThatThrownBy(()->vault.open("other:1",cipher)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->vault.open("owner:2",cipher)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new CredentialVault("").seal("owner:1","secret")).isInstanceOf(IllegalStateException.class);
    }
    @Test void projectionDatabaseAndAuditDoNotExposeTokens() {
        var c=create(System.currentTimeMillis()+3600000);
        assertThat(repo.encode(service.state())).doesNotContain("access-original","refresh-original","identity-original","secret_cipher");
        assertThat(jdbc.queryForObject("SELECT secret_cipher FROM model_connection",String.class)).doesNotContain("access-original","refresh-original");
        assertThat(jdbc.queryForList("SELECT detail FROM authority_audit").toString()).doesNotContain("refresh-original");
        assertThat(c.state()).isEqualTo("READY");
    }
    @Test void exactLeaseGetsOnlyAnAccessTokenAndFrozenModel() {
        var c=create(System.currentTimeMillis()+3600000);bind(c);
        var token=service.access("task",input(c),worker(c));
        assertThat(token.accessToken()).isEqualTo("access-original");assertThat(repo.encode(token)).doesNotContain("refresh-original","identity-original");
        assertThat(token.toString()).doesNotContain("access-original");verify(guard,times(2)).verifyLease(eq("task"),eq(input(c).lease()),eq(true),eq(true));
        var wrong=new Access(input(c).lease(),c.id(),c.version(),"other-model",null);
        assertThatThrownBy(()->service.access("task",wrong,worker(c))).isInstanceOf(SecurityException.class);
    }
    @Test void anotherOwnerAndStaleLeaseCannotReadCredential() {
        var c=create(System.currentTimeMillis()+3600000);bind(c);
        assertThatThrownBy(()->service.owned(c.id(),c.version(),"other")).isInstanceOf(SecurityException.class);
        doThrow(new SecurityException()).when(guard).verifyLease(anyString(),any(),eq(true),eq(true));
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(SecurityException.class);verifyNoInteractions(oauth);
    }
    @Test void expiredCredentialRotatesOnceAndReusesNewGeneration() {
        var c=create(System.currentTimeMillis()+1000);bind(c);
        when(oauth.refresh(any(),anyString(),anyString(),any())).thenAnswer(call->{
            assertThat(jdbc.queryForObject("SELECT state FROM model_connection",String.class)).isEqualTo("REFRESHING");
            return issued("rotated",System.currentTimeMillis()+3600000);
        });
        assertThat(service.access("task",input(c),worker(c)).generation()).isEqualTo(2);
        assertThat(service.access("task",input(c),worker(c)).accessToken()).isEqualTo("access-rotated");
        verify(oauth,times(1)).refresh(any(),anyString(),anyString(),any());
    }
    @Test void concurrentCallersShareOneDurableRefresh() throws Exception {
        var c=create(System.currentTimeMillis()+1000);bind(c);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(oauth.refresh(any(),anyString(),anyString(),any())).thenAnswer(call->{entered.countDown();if(!release.await(2,TimeUnit.SECONDS))throw new IllegalStateException();return issued("rotated",System.currentTimeMillis()+3600000);});
        var first=CompletableFuture.supplyAsync(()->service.access("task",input(c),worker(c)));
        try {
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var second=CompletableFuture.supplyAsync(()->service.access("task",input(c),worker(c)));
            release.countDown();
            assertThat(first.get(3,TimeUnit.SECONDS).generation()).isEqualTo(2);assertThat(second.get(3,TimeUnit.SECONDS).generation()).isEqualTo(2);
            verify(oauth,times(1)).refresh(any(),anyString(),anyString(),any());
        }finally {release.countDown();}
    }
    @Test void lostRefreshResponseIsDurablyUnknownAndNeverReplayed() {
        var c=create(System.currentTimeMillis()+1000);bind(c);when(oauth.refresh(any(),anyString(),anyString(),any())).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).hasMessage("MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT state FROM model_connection",String.class)).isEqualTo("REFRESH_OUTCOME_UNKNOWN");
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(IllegalStateException.class);
        verify(oauth,times(1)).refresh(any(),anyString(),anyString(),any());
    }
    @Test void disconnectWinsOverALateRefreshResponse() {
        var c=create(System.currentTimeMillis()+1000);bind(c);
        when(oauth.refresh(any(),anyString(),anyString(),any())).thenAnswer(call->{service.disconnect(new Selection(c.id(),c.version()));return issued("late",System.currentTimeMillis()+3600000);});
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(IllegalStateException.class);
        var row=service.row(c.id());assertThat(row.get("state")).isEqualTo("DISCONNECTED");assertThat(row.get("secret_cipher")).isNull();
    }
    @Test void importingTheSameRotatingTokenSetTwiceIsRejected() {
        create(System.currentTimeMillis()+3600000);
        assertThatThrownBy(()->service.save("another",Kind.CHATGPT_RESPONSES,"Other",issued("original",System.currentTimeMillis()+3600000),null))
                .hasMessage("MODEL_TOKEN_ALREADY_IMPORTED_OR_USED");
    }
    @Test void planAndQuestionSuccessorKeepTheirOriginalConnection() {
        var c=create(System.currentTimeMillis()+3600000);var task=mock(WorkTask.class);
        when(task.id()).thenReturn(new TaskId("task"));when(task.subjectType()).thenReturn("flexible-plan");when(task.subjectId()).thenReturn("plan");
        var worker=worker(c);register(c);service.claimed(task,worker);
        service.replaced(task.id(),new TaskId("successor"));
        assertThat(jdbc.queryForObject("SELECT connection_id FROM model_auth_task WHERE task_id='successor'",String.class)).isEqualTo(c.id());
        var local=mock(Worker.class);when(local.capabilities()).thenReturn(Set.of());
        assertThat(service.canClaim(task,local)).isFalse();
        var changed=service.save(actor,Kind.CHATGPT_RESPONSES,"Another",new Issued(issued("other",System.currentTimeMillis()+3600000).tokens(),"oaiapp_other","other-subject",null,null,System.currentTimeMillis()+3600000,0,true),null);
        assertThat(service.canClaim(task,worker(changed))).isFalse();
    }
    @Test void callbackRejectsDuplicateParametersAndConsoleRoutesRemainProtected() {
        assertThatThrownBy(()->OpenAiLogin.parse("state=first&state=second")).isInstanceOf(IllegalArgumentException.class);
        assertThat(me.asu.aidev.webconsole.security.WebConsoleApiRoutes.matches("/api/r1/model-connections/import")).isTrue();
        assertThat(me.asu.aidev.webconsole.security.WebConsoleApiRoutes.matches("/api/r1/worker-protocol/v1/tasks/task/model-credential")).isFalse();
    }
    @Test void advertisedCapabilityWithoutServerWorkerBindingCannotClaimOrAccess() {
        var c=create(System.currentTimeMillis()+3600000);var task=mock(WorkTask.class);
        when(task.id()).thenReturn(new TaskId("task"));when(task.subjectType()).thenReturn("flexible-plan");when(task.subjectId()).thenReturn("plan");
        assertThat(service.canClaim(task,worker(c))).isFalse();
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.validateRegistration(new me.asu.aidev.worker.application.RegisterWorkerCommand(new WorkerId("worker"),"openai-agents:coder",worker(c).capabilities(),1))).isInstanceOf(SecurityException.class);
    }
    @Test void publicWorkerIdentityWithoutPrivateProofCannotRegisterClaimOrReadTokens() {
        var c=create(System.currentTimeMillis()+3600000);bind(c);doReturn("").when(service).proofHeader();
        var task=mock(WorkTask.class);when(task.id()).thenReturn(new TaskId("task"));when(task.subjectType()).thenReturn("flexible-plan");when(task.subjectId()).thenReturn("plan");
        assertThatThrownBy(()->service.validateRegistration(new me.asu.aidev.worker.application.RegisterWorkerCommand(new WorkerId("worker"),"openai-agents:coder",worker(c).capabilities(),1))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.claimed(task,worker(c))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(SecurityException.class);verifyNoInteractions(oauth);
    }
    @Test void registeredWorkerCannotChangeItsModelConnectionOrRuntime() {
        var c=create(System.currentTimeMillis()+3600000);register(c);
        assertThatCode(()->service.validateRegistration(new me.asu.aidev.worker.application.RegisterWorkerCommand(new WorkerId("worker"),"openai-agents:coder",worker(c).capabilities(),1))).doesNotThrowAnyException();
        assertThatThrownBy(()->service.validateRegistration(new me.asu.aidev.worker.application.RegisterWorkerCommand(new WorkerId("worker"),"codex-app-server:coder",worker(c).capabilities(),1))).isInstanceOf(SecurityException.class);
        assertThat(jdbc.queryForObject("SELECT proof_cipher FROM model_auth_worker",String.class)).doesNotContain(service.proofHeader());
    }
    @Test void aNewAuthorizationRecoversAnExpiredRefreshIntentWithoutReplayingIt() {
        var c=create(System.currentTimeMillis()+1000);
        jdbc.update("UPDATE model_connection SET state='REFRESHING',refresh_started_ms=?",System.currentTimeMillis()-60000);
        var prior=service.row(c.id());var recovered=service.save(actor,Kind.CHATGPT_RESPONSES,"My connection",issued("reauthorized",System.currentTimeMillis()+3600000),prior);
        assertThat(recovered.state()).isEqualTo("READY");assertThat(recovered.generation()).isEqualTo(2);verifyNoInteractions(oauth);
    }
    @Test void aLateOldRefreshCannotOverwriteAnExplicitRecoveredAuthorization() {
        var c=create(System.currentTimeMillis()+1000);bind(c);
        when(oauth.refresh(any(),anyString(),anyString(),any())).thenAnswer(call->{
            jdbc.update("UPDATE model_connection SET refresh_started_ms=?",System.currentTimeMillis()-60000);
            service.save(actor,Kind.CHATGPT_RESPONSES,"My connection",issued("reauthorized",System.currentTimeMillis()+3600000),service.row(c.id()));
            return issued("old-late-reply",System.currentTimeMillis()+3600000);
        });
        assertThatThrownBy(()->service.access("task",input(c),worker(c))).isInstanceOf(IllegalStateException.class);
        assertThat(service.row(c.id()).get("state")).isEqualTo("READY");
        assertThat(service.access("task",input(c),worker(c)).accessToken()).isEqualTo("access-reauthorized");verify(oauth,times(1)).refresh(any(),anyString(),anyString(),any());
    }
}
