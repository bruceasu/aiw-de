package me.asu.aidev.execution.guard;

import java.nio.file.Path;
import java.util.*;
import me.asu.aidev.authority.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static me.asu.aidev.execution.guard.GuardContract.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolApprovalsTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;ToolApprovals approvals;ExecutionGuard guard;TransactionTemplate transaction;
    final String hash="a".repeat(64),workspace="b".repeat(64),version="c".repeat(64);
    final Lease lease=new Lease("worker","attempt","lease");
    Begin operation;ToolApprovals.Binding binding;ToolApprovals.Consume consume;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("approval.db"));jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE authority_lock(id INTEGER,version INTEGER)");jdbc.update("INSERT INTO authority_lock VALUES(1,0)");
        jdbc.execute("CREATE TABLE execution_context(task_id TEXT PRIMARY KEY)");jdbc.update("INSERT INTO execution_context VALUES('task')");
        jdbc.execute("CREATE TABLE execution_policy(plan_id TEXT PRIMARY KEY)");jdbc.update("INSERT INTO execution_policy VALUES('plan')");
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY)");jdbc.update("INSERT INTO execution_attempt VALUES('attempt')");
        jdbc.execute("CREATE TABLE execution_yield(task_id TEXT PRIMARY KEY,state TEXT)");jdbc.update("INSERT INTO execution_yield VALUES('task','ACKNOWLEDGED')");
        jdbc.execute("CREATE TABLE authority_audit(id TEXT,target TEXT,grant_id TEXT,request_id TEXT,actor TEXT,event TEXT,detail TEXT,created_ms BIGINT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__execution_tool_approvals.sql"),
                new ClassPathResource("db/migration/V34__tool_approval_consumption.sql"),
                new ClassPathResource("db/migration/V35__tool_approval_successors.sql")).execute(ds);
        var repo=new AuthorityRepository(jdbc);var boundary=new AuthorityBoundary(repo);guard=mock(ExecutionGuard.class);
        var yields=mock(ExecutionYieldService.class);
        approvals=new ToolApprovals(repo,boundary,guard,yields,mock(me.asu.aidev.work.port.WorkTaskRepository.class));transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        operation=new Begin(PROTOCOL,"instance",lease,"operation",Tool.WRITE,"repo","file.txt",hash,1);
        binding=new ToolApprovals.Binding(ToolApprovals.PROTOCOL,"approval",operation,ToolApprovals.Action.DELETE_FILE,workspace,version,System.currentTimeMillis()+60000,false,
                null,new FrozenRef("tool-approval","approval","a".repeat(40),"d".repeat(64)));
        var policy=new HashMap<String,Object>();policy.put("owner_id","owner");policy.put("digest",hash);policy.put("generation",0L);policy.put("revoked",false);
        when(guard.policyRow("plan")).thenReturn(policy);when(guard.contextRow("task")).thenReturn(Map.of("plan_id","plan"));
        String json=repo.encode(binding),digest=AuthorityRepository.hash(json);
        jdbc.update("""
            INSERT INTO execution_tool_approval(id,task_id,plan_id,owner_id,attempt_id,operation_id,binding_json,binding_digest,
            request_digest,policy_digest,generation,state,expires_ms,created_ms) VALUES('approval','task','plan','owner','attempt','operation',?,?,?, ?,0,'APPROVED',?,?)
            """,json,digest,hash,hash,binding.expiresAt(),System.currentTimeMillis());
        consume=new ToolApprovals.Consume("approval",digest,operation,ToolApprovals.Action.DELETE_FILE,workspace,version);
        when(guard.begin("task",operation)).thenReturn(new ExecutionGuard.Receipt(PROTOCOL,"operation","STARTED",true,"AUTHORIZED_START"));
        when(guard.query("task",lease,"operation")).thenReturn(new ExecutionGuard.Receipt(PROTOCOL,"operation","STARTED",false,"QUERY_ONLY"));
    }
    ExecutionGuard.Receipt consume(ToolApprovals.Consume input) {return transaction.execute(status->approvals.consume("task",input));}
    String state() {return jdbc.queryForObject("SELECT state FROM execution_tool_approval",String.class);}
    @Test void duplicateConsumptionOnlyQueriesOriginalOutcome() {
        assertThat(consume(consume).execute()).isTrue();assertThat(consume(consume).execute()).isFalse();
        verify(guard,times(1)).begin("task",operation);verify(guard).requireApprovalScope("task",operation);
        assertThat(state()).isEqualTo("CONSUMED");
    }
    @Test void targetVersionChangesCannotStartAnOperation() {
        var changed=new ToolApprovals.Consume("approval",consume.bindingDigest(),operation,consume.action(),workspace,"d".repeat(64));
        assertThatThrownBy(()->consume(changed)).isInstanceOf(SecurityException.class);
        verify(guard,never()).begin(anyString(),any());assertThat(state()).isEqualTo("APPROVED");
    }
    @Test void failedStartRollsBackConsumption() {
        when(guard.begin("task",operation)).thenThrow(new IllegalStateException("changed"));
        assertThatThrownBy(()->consume(consume)).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo("APPROVED");
    }
    @Test void expiredConsentCannotStart() {
        jdbc.update("UPDATE execution_tool_approval SET expires_ms=1");
        assertThatThrownBy(()->consume(consume)).isInstanceOf(IllegalStateException.class);
        verify(guard,never()).begin(anyString(),any());
    }
}
