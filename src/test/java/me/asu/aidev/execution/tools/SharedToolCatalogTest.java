package me.asu.aidev.execution.tools;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.guard.GuardContract;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import me.asu.aidev.worker.protocol.http.WorkerProtocolV1Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedToolCatalogTest {
    @TempDir Path directory;
    SharedToolCatalog catalog;
    AuthorityBoundary boundary;
    AuthorityRepository repository;
    DriverManagerDataSource dataSource;

    @BeforeEach void setup() {
        dataSource=new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("tools.db"));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__shared_tool_catalog.sql")).execute(dataSource);
        var jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE authority_lock(id INTEGER PRIMARY KEY,version BIGINT)");
        jdbc.update("INSERT INTO authority_lock VALUES(1,0)");
        jdbc.execute("""
                CREATE TABLE authority_audit(id TEXT PRIMARY KEY,target_key TEXT,grant_id TEXT,request_id TEXT,
                   actor TEXT,event TEXT,detail TEXT,created_ms BIGINT)
                """);
        repository=new AuthorityRepository(jdbc);
        boundary=new AuthorityBoundary(repository);
        catalog=new SharedToolCatalog(repository,boundary);
    }

    @Test void authorizesExactRevisionWithLiveLeaseAndStopsAfterRevocationOrUnknownResult() {
        var jdbc=repository.jdbc();
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_id TEXT,subject_type TEXT,lease_token TEXT)");
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE plan_run_owner(plan_id TEXT,actor TEXT)");
        jdbc.update("INSERT INTO work_task VALUES('task','plan','flexible-plan',NULL)");
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt')");
        jdbc.update("INSERT INTO plan_run_owner VALUES('plan','admin')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__shared_tool_task_access.sql")).execute(dataSource);
        catalog.save("admin",edit("lookup",0,SharedToolCatalog.Kind.HTTP,"Lookup",true));
        var guard=mock(ExecutionGuard.class);
        var access=new SharedToolAccess(repository,boundary,guard,catalog,new DataSourceTransactionManager(dataSource),
                "https://private.example.org/tools");
        var lease=new GuardContract.Lease("worker","attempt","token");
        var ref=new SharedToolAccess.Ref("lookup",1);
        var id="c115dd5c-b99b-4d01-969b-5c45330e4c80";
        assertThatThrownBy(()->access.invoke("task",new SharedToolAccess.Call(lease,id,ref,Map.of())))
                .isInstanceOf(SecurityException.class);
        access.bind("task",java.util.List.of(ref));
        assertThat(access.discover("task",lease)).extracting(SharedToolCatalog.Item::id).containsExactly("lookup");
        verify(guard,atLeastOnce()).verifyLease("task",lease,true,true);
        assertThatThrownBy(()->access.invoke("task",new SharedToolAccess.Call(lease,id,ref,Map.of("unexpected","value"))))
                .isInstanceOf(IllegalArgumentException.class);
        jdbc.update("""
                INSERT INTO shared_tool_operation(id,task_id,attempt_id,worker_id,tool_id,tool_revision,request_digest,state,created_ms)
                VALUES(?,'task','attempt','worker','lookup',1,?,'UNKNOWN',1)
                """,id,AuthorityRepository.hash("{}"));
        assertThatThrownBy(()->access.requireSettled("task")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->access.reconcile("another","task",new SharedToolAccess.Reconciliation(id,"Reviewed")))
                .isInstanceOf(SecurityException.class);
        access.reconcile("admin","task",new SharedToolAccess.Reconciliation(id,"Reviewed"));
        assertThat(access.receipts("admin","task")).extracting(SharedToolAccess.Receipt::state).containsExactly("RECONCILED");
        access.revoke("admin","task",ref);
        assertThat(access.discover("task",lease)).isEmpty();
        assertThatThrownBy(()->access.invoke("task",new SharedToolAccess.Call(lease,id,ref,Map.of())))
                .isInstanceOf(SecurityException.class);
    }

    @Test void registersBothKindsSearchesLiteralTermsAndDetectsStaleEdits() {
        var mcp=catalog.save("admin",edit("a-receipt",0,SharedToolCatalog.Kind.MCP,"Receipt 100%",true));
        catalog.save("admin",edit("b-inventory",0,SharedToolCatalog.Kind.HTTP,"Inventory",true));
        assertThat(mcp.endpoint()).isEqualTo("https://private.example.org/tools");
        var page=catalog.search("",null,null,null,1);
        assertThat(page.items()).extracting(SharedToolCatalog.Item::id).containsExactly("a-receipt");
        assertThat(catalog.search("",null,null,page.next(),1).items())
                .extracting(SharedToolCatalog.Item::id).containsExactly("b-inventory");
        assertThat(catalog.search("%",SharedToolCatalog.Kind.MCP,null,null,10).items())
                .extracting(SharedToolCatalog.Item::id).containsExactly("a-receipt");
        assertThat(catalog.search("%",SharedToolCatalog.Kind.HTTP,null,null,10).items()).isEmpty();
        var disabled=catalog.save("admin",edit("a-receipt",1,SharedToolCatalog.Kind.MCP,"Receipt 100%",false));
        assertThat(disabled.tool().revision()).isEqualTo(2);
        assertThatThrownBy(()->catalog.save("admin",edit("a-receipt",1,SharedToolCatalog.Kind.MCP,"Stale",true)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.jdbc().queryForList("SELECT detail FROM authority_audit WHERE target_key='SHARED_TOOL:a-receipt' ORDER BY detail",String.class))
                .containsExactly("v1 ENABLED","v2 DISABLED");
    }

    @Test void workerDiscoveryRequiresMatchingIdentityAndReturnsEnabledMetadata() {
        catalog.save("admin",edit("enabled",0,SharedToolCatalog.Kind.MCP,"Online",true));
        catalog.save("admin",edit("disabled",0,SharedToolCatalog.Kind.HTTP,"Hidden",false));
        var workers=mock(WorkerService.class);
        var id=new WorkerId("worker-a");
        var now=Instant.now();
        when(workers.get(id)).thenReturn(new Worker(id,"openai-agents:task",Set.of(),1,0,WorkerStatus.ACTIVE,now,now,now));
        var api=new WorkerProtocolV1Controller(workers,null,null,null,null);
        ReflectionTestUtils.setField(api,"authorityBoundary",boundary);
        ReflectionTestUtils.setField(api,"sharedTools",catalog);
        var query=new WorkerProtocolV1Controller.ToolSearch("",null,null,25);
        boundary.workerIdentity("*");
        assertThatThrownBy(()->api.searchToolCatalog("worker-a",null,query)).isInstanceOf(ResponseStatusException.class);
        boundary.workerIdentity("worker-b");
        assertThatThrownBy(()->api.searchToolCatalog("worker-a","Bearer other-worker",query)).isInstanceOf(ResponseStatusException.class);
        boundary.workerIdentity("worker-a");
        var result=api.searchToolCatalog("worker-a","Bearer matching-worker",query);
        assertThat(result.items()).extracting(SharedToolCatalog.Item::id).containsExactly("enabled");
        assertThat(repository.encode(result)).doesNotContain("private.example.org","endpoint");
    }

    private SharedToolCatalog.Edit edit(String id,long revision,SharedToolCatalog.Kind kind,String title,boolean enabled) {
        return new SharedToolCatalog.Edit(id,revision,kind,title,"Tool description","https://private.example.org/tools",
                "lookup",kind==SharedToolCatalog.Kind.HTTP?"GET":null,Map.of("type","object"),enabled);
    }
}
