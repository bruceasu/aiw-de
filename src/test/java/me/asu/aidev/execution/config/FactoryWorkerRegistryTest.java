package me.asu.aidev.execution.config;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.assertj.core.api.Assertions.*;

class FactoryWorkerRegistryTest {
    @TempDir Path temp;
    JdbcTemplate db;
    FactoryWorkerRegistry registry;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("registry.db"));
        db=new JdbcTemplate(source);
        db.execute("CREATE TABLE worker(id TEXT PRIMARY KEY,status TEXT,active_leases INTEGER,last_heartbeat_at_epoch_ms BIGINT,updated_at_epoch_ms BIGINT DEFAULT 0)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,current_worker_id TEXT,state TEXT,skill_ref TEXT,repository_ref TEXT)");
        db.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,worker_id TEXT,state TEXT)");
        db.execute(Files.readString(Path.of("src/main/resources/db/migration/V22__factory_worker_registration.sql")));
        registry=new FactoryWorkerRegistry(db,new DataSourceTransactionManager(source));
    }
    private void worker(String id,String status,int leases,long heartbeat,boolean factory) {
        db.update("INSERT INTO worker(id,status,active_leases,last_heartbeat_at_epoch_ms) VALUES(?,?,?,?)",id,status,leases,heartbeat);
        if(factory)registry.remember(id,"key");
    }
    private boolean retired(String id) {
        return db.queryForObject("SELECT COUNT(*) FROM factory_worker_registration WHERE worker_id=? AND retired_at_epoch_ms IS NOT NULL",Integer.class,id)>0;
    }
    @Test void retiresOfflineRegistrationAndPreservesHistory() {
        worker("owned","OFFLINE",0,0,true);
        db.update("INSERT INTO execution_attempt VALUES('done','owned','COMPLETED')");
        registry.retireUnavailable();registry.retireUnavailable();
        assertThat(retired("owned")).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM worker",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM execution_attempt",Integer.class)).isEqualTo(1);
    }
    @Test void prefixAloneNeverRetiresExternalWorker() {
        String id="factory-0123456789abcdef01234567";
        worker(id,"OFFLINE",0,0,false);registry.retireUnavailable();
        assertThat(retired(id)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM factory_worker_registration",Integer.class)).isZero();
    }
    @Test void onlineWorkerRemainsReusable() {
        worker("online","ACTIVE",0,System.currentTimeMillis(),true);registry.retireUnavailable();
        assertThat(retired("online")).isFalse();assertThat(registry.online("online")).isTrue();
    }
    @Test void expiredHeartbeatRetiresOnlyUnleasedWorker() {
        worker("stale","ACTIVE",0,0,true);registry.retireUnavailable();
        assertThat(retired("stale")).isTrue();
        assertThat(db.queryForObject("SELECT status FROM worker WHERE id='stale'",String.class)).isEqualTo("ACTIVE");
        db.update("UPDATE worker SET last_heartbeat_at_epoch_ms=? WHERE id='stale'",System.currentTimeMillis());
        registry.heartbeat("stale");assertThat(retired("stale")).isFalse();
        registry.retireUnavailable();assertThat(retired("stale")).isFalse();
    }
    @Test void activeLeaseAndTaskAndAttemptEachProtectRegistration() {
        worker("lease","OFFLINE",1,0,true);worker("task","OFFLINE",0,0,true);worker("attempt","OFFLINE",0,0,true);
        db.update("INSERT INTO work_task(id,current_worker_id,state) VALUES('task1','task','RUNNING')");
        db.update("INSERT INTO execution_attempt VALUES('attempt1','attempt','CLAIMED')");
        registry.retireUnavailable();
        for(String id:new String[]{"lease","task","attempt"}) {
            assertThat(retired(id)).isFalse();assertThat(registry.busy(id)).isTrue();
        }
    }
    @Test void registrationRestoresExistingIdentity() {
        worker("owned","OFFLINE",0,0,true);registry.retireUnavailable();
        registry.heartbeat("owned");assertThat(retired("owned")).isTrue();
        registry.registered("owned");
        assertThat(retired("owned")).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM factory_worker_registration",Integer.class)).isEqualTo(1);
    }
    @Test void compatibilityUsesActualConfigurationAndRole() {
        var one=new AgentFactorySettings.Settings("codex-app-server","model","medium",true,"python","codex","");
        var same=new AgentFactorySettings.Settings("codex-app-server","model","medium",false,"python","codex","");
        assertThat(ManagedWorkerProvisioner.compatibilityKey("planner",null,one))
                .isEqualTo(ManagedWorkerProvisioner.compatibilityKey("planner",null,same))
                .isNotEqualTo(ManagedWorkerProvisioner.compatibilityKey("goal-discussion",null,one))
                .isNotEqualTo(ManagedWorkerProvisioner.compatibilityKey("planner","repo",one));
    }
    @Test void idleOwnedWorkerDrainsBeforeRetirement() {
        worker("idle","ACTIVE",0,System.currentTimeMillis(),true);
        assertThat(registry.drainIdle("idle","planner","repo")).isTrue();
        assertThat(registry.online("idle")).isFalse();
        registry.stopped("idle");
        assertThat(retired("idle")).isTrue();
        assertThat(db.queryForObject("SELECT status FROM worker WHERE id='idle'",String.class)).isEqualTo("OFFLINE");
    }
    @Test void leaseTaskAttemptAndMatchingReadyEachPreventDrain() {
        worker("lease","ACTIVE",1,0,true);worker("task","ACTIVE",0,0,true);worker("attempt","ACTIVE",0,0,true);
        db.update("INSERT INTO work_task(id,current_worker_id,state) VALUES('active','task','RUNNING')");
        db.update("INSERT INTO execution_attempt VALUES('active','attempt','CLAIMED')");
        for(String id:new String[]{"lease","task","attempt"}) assertThat(registry.drainIdle(id,"planner","repo")).isFalse();
        worker("ready","ACTIVE",0,0,true);
        db.update("INSERT INTO work_task(id,state,skill_ref,repository_ref) VALUES('queued','READY','planner','repo')");
        assertThat(registry.drainIdle("ready","planner","repo")).isFalse();
        assertThat(registry.drainIdle("ready","planner","other")).isTrue();
    }
}
