package me.asu.aidev.modelauth;

import java.nio.file.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static me.asu.aidev.modelauth.ModelAuthContract.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All OS protection is a fake; these tests never invoke PowerShell or a user's key store. */
class LocalModelVaultTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;AuthorityRepository repo;DataSourceTransactionManager manager;AuthorityBoundary boundary;
    CredentialVault vault;ModelConnections connections;Path keys;
    final String actor="web:admin";
    final LocalModelKeyStore.Protector protector=new LocalModelKeyStore.Protector() {
        public byte[] protect(byte[] value) {
            byte[] result=new byte[value.length+1];result[0]=37;
            for(int i=0;i<value.length;i++)result[i+1]=(byte)(value[i]^0x5a);return result;
        }
        public byte[] unprotect(byte[] value) {
            if(value.length!=33||value[0]!=37)throw new IllegalStateException("MODEL_LOCAL_KEY_UNAVAILABLE");
            byte[] result=new byte[32];for(int i=0;i<result.length;i++)result[i]=(byte)(value[i+1]^0x5a);return result;
        }
    };
    @BeforeEach void setup() {
        var data=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("local.db"));jdbc=new JdbcTemplate(data);manager=new DataSourceTransactionManager(data);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V30__model_credentials.sql"),new ClassPathResource("db/migration/V31__model_worker_proofs.sql"),
                new ClassPathResource("db/migration/V32__local_model_vault.sql")).execute(data);
        jdbc.execute("CREATE TABLE authority_audit(id TEXT,target_key TEXT,grant_id TEXT,request_id TEXT,actor TEXT,event TEXT,detail TEXT,created_ms BIGINT)");
        repo=new AuthorityRepository(jdbc);boundary=mock(AuthorityBoundary.class);when(boundary.human()).thenReturn(actor);keys=temp.resolve("protected-keys");
        vault=freshVault();connections=new ModelConnections(repo,boundary,mock(AuthorityGrantService.class),vault,mock(OpenAiOAuth.class),mock(ExecutionGuard.class),manager);
    }
    CredentialVault freshVault() {return new CredentialVault("",repo,manager,new LocalModelKeyStore(keys,protector));}
    String revision() {return vault.status().revision();}
    Path keyFile(String id) {return keys.resolve(id+".dpapi");}
    Issued issued(String token) {return new Issued(new TokenSet("access-"+token,"refresh-"+token,"id-"+token,"scope"),"client","subject",null,null,System.currentTimeMillis()+3600000,0,true);}
    Connection create(String owner,String token) {return connections.save(owner,Kind.CHATGPT_RESPONSES,"Account",issued(token),null);}

    @Test void firstInstallationGeneratesProtectedKeyAndRestartReusesIt() throws Exception {
        assertThat(vault.ready()).isTrue();String id=revision();String encrypted=vault.seal("owner","credential");
        assertThat(keyFile(id)).exists();assertThat(Files.size(keyFile(id))).isEqualTo(33);
        assertThat(jdbc.queryForMap("SELECT * FROM model_vault_local")).containsKeys("key_id","key_digest").doesNotContainKey("key");
        assertThat(freshVault().open("owner",encrypted)).isEqualTo("credential");assertThat(revision()).isEqualTo(id);
    }
    @Test void missingProtectedFileDoesNotReplaceExistingCredentials() throws Exception {
        create(actor,"original");String id=revision();Files.delete(keyFile(id));
        assertThat(vault.status().state()).isEqualTo("MODEL_LOCAL_KEY_MISSING");assertThat(vault.ready()).isFalse();
        assertThat(keyFile(id)).doesNotExist();assertThat(revision()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_connection WHERE secret_cipher IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @Test void legacyCiphertextWithoutManagedKeyRequiresExplicitRecovery() {
        var legacy=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]));
        var service=new ModelConnections(repo,boundary,mock(AuthorityGrantService.class),legacy,mock(OpenAiOAuth.class),mock(ExecutionGuard.class),manager);
        service.save(actor,Kind.CHATGPT_RESPONSES,"Account",issued("legacy"),null);
        assertThat(vault.status().state()).isEqualTo("MODEL_LOCAL_KEY_MISSING");assertThat(keyFile(revision())).doesNotExist();
    }
    @Test void damagedProtectedFileFailsClosedInsteadOfGeneratingAnotherKey() throws Exception {
        vault.ready();String id=revision();Files.write(keyFile(id),new byte[]{1,2,3});
        assertThat(vault.status().state()).isEqualTo("MODEL_LOCAL_KEY_UNAVAILABLE");assertThat(Files.readAllBytes(keyFile(id))).containsExactly(1,2,3);
    }
    @Test void replacingFileWithAnotherValidProtectedKeyIsDetected() throws Exception {
        vault.ready();Files.write(keyFile(revision()),protector.protect(new byte[32]));
        assertThat(vault.status().state()).isEqualTo("MODEL_LOCAL_KEY_CHANGED");
    }
    @Test void resetClearsOwnConnectionsAndProofButPreservesOldPlanAndTaskReferences() throws Exception {
        var connection=create(actor,"old");String old=revision();
        connections.registerWorker("worker",connection.id(),connection.version(),"openai-agents","model");
        jdbc.update("INSERT INTO model_auth_task VALUES('task',?,?,?,'openai-agents','model')",connection.id(),connection.version(),actor);
        jdbc.update("INSERT INTO model_auth_plan VALUES('flexible-plan','plan',?,?)",connection.id(),connection.version());
        Files.delete(keyFile(old));var result=connections.resetLocalStorage(new ResetLocalStorage(old,true));
        assertThat(result.get("remoteRevocationConfirmed")).isEqualTo(false);assertThat(result.get("oldKeyRemoved")).isEqualTo(true);
        assertThat(vault.ready()).isTrue();assertThat(revision()).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_connection",Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM model_auth_worker")).containsEntry("proof_digest",null).containsEntry("proof_cipher",null);
        assertThat(jdbc.queryForObject("SELECT connection_id FROM model_auth_task",String.class)).isEqualTo(connection.id());
        assertThat(jdbc.queryForObject("SELECT connection_id FROM model_auth_plan",String.class)).isEqualTo(connection.id());
        assertThat(create(actor,"new").id()).isNotEqualTo(connection.id());
    }
    @Test void resetRejectsForeignOwnerAndRollsBackEpoch() {
        create("web:other","other");String old=revision();
        assertThatThrownBy(()->connections.resetLocalStorage(new ResetLocalStorage(old,true))).hasMessage("MODEL_LOCAL_KEY_OTHER_OWNERS");
        assertThat(revision()).isEqualTo(old);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_connection",Integer.class)).isEqualTo(1);
    }
    @Test void resetRequiresConfirmationAndAnUndelegatedConfigurationAdmin() {
        String old=revision();assertThatThrownBy(()->connections.resetLocalStorage(new ResetLocalStorage(old,false))).isInstanceOf(IllegalArgumentException.class);
        when(boundary.delegated()).thenReturn(true);
        assertThatThrownBy(()->connections.resetLocalStorage(new ResetLocalStorage(old,true))).isInstanceOf(SecurityException.class);
        assertThat(revision()).isEqualTo(old);
    }
    @Test void oldAuthorizationAndRepeatedResetCannotOverwriteNewEpoch() {
        String old=revision();connections.resetLocalStorage(new ResetLocalStorage(old,true));
        assertThatThrownBy(()->connections.save(actor,Kind.CHATGPT_RESPONSES,"late login",issued("late"),null,old)).hasMessage("MODEL_LOCAL_KEY_CHANGED");
        assertThatThrownBy(()->connections.resetLocalStorage(new ResetLocalStorage(old,true))).hasMessage("MODEL_LOCAL_KEY_CHANGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_connection",Integer.class)).isZero();
    }
    @Test void explicitOverrideDoesNotCreateLocalKeyAndInvalidOverrideCannotFallBack() {
        var explicit=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]),repo,manager,new LocalModelKeyStore(keys,protector));
        assertThat(explicit.status().mode()).isEqualTo("CONFIGURED");assertThat(explicit.ready()).isTrue();
        var invalid=new CredentialVault("invalid",repo,manager,new LocalModelKeyStore(keys,protector));
        assertThat(invalid.status().state()).isEqualTo("MODEL_CREDENTIAL_KEY_INVALID");assertThat(invalid.ready()).isFalse();assertThat(keys).doesNotExist();
    }
    @Test void anotherPlaneCannotSilentlyUseADifferentConfiguredKeyForTheSameDatabase() {
        vault.ready();var different=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]),repo,manager,new LocalModelKeyStore(keys,protector));
        assertThat(different.status().state()).isEqualTo("MODEL_LOCAL_KEY_CHANGED");assertThat(different.ready()).isFalse();
        assertThat(vault.ready()).isTrue();
    }
    @Test void wrongExplicitKeyCannotPoisonLegacyDatabaseFingerprint() {
        byte[] original=new byte[32];Arrays.fill(original,(byte)7);
        var legacy=new CredentialVault(Base64.getEncoder().encodeToString(original));
        new ModelConnections(repo,boundary,mock(AuthorityGrantService.class),legacy,mock(OpenAiOAuth.class),mock(ExecutionGuard.class),manager)
                .save(actor,Kind.CHATGPT_RESPONSES,"Legacy account",issued("legacy"),null);
        var wrong=new CredentialVault(Base64.getEncoder().encodeToString(new byte[32]),repo,manager,new LocalModelKeyStore(keys,protector));
        assertThat(wrong.status().ready()).isFalse();assertThat(jdbc.queryForMap("SELECT * FROM model_vault_local").get("key_digest")).isNull();
        var correct=new CredentialVault(Base64.getEncoder().encodeToString(original),repo,manager,new LocalModelKeyStore(keys,protector));
        assertThat(correct.ready()).isTrue();assertThat(keys).doesNotExist();
    }
    private Process helper(InputStream output,OutputStream input,boolean aliveInitially) throws Exception {
        var process=mock(Process.class);var alive=new AtomicBoolean(aliveInitially);
        when(process.getInputStream()).thenReturn(output);when(process.getOutputStream()).thenReturn(input);when(process.getErrorStream()).thenReturn(InputStream.nullInputStream());
        when(process.isAlive()).thenAnswer(ignored->alive.get());when(process.waitFor(anyLong(),any(TimeUnit.class))).thenReturn(true);
        when(process.destroyForcibly()).thenAnswer(ignored->{alive.set(false);return process;});return process;
    }
    @Test void blockedHelperInputCannotPreventTheDeadlineOrProcessCleanup() throws Exception {
        var input=new OutputStream() {
            public void write(int value) throws IOException {
                try {new CountDownLatch(1).await();}catch(InterruptedException error) {Thread.currentThread().interrupt();throw new InterruptedIOException();}
            }
        };
        var process=helper(new ByteArrayInputStream("eA==".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),input,true);
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3),()->assertThatThrownBy(()->LocalModelKeyStore.exchange(process,new byte[16384],50)).hasMessage("MODEL_LOCAL_KEY_UNAVAILABLE"));
        verify(process).destroyForcibly();
    }
    @Test void blockedHelperOutputCannotMakeExecutorShutdownWaitIndefinitely() throws Exception {
        var output=new InputStream() {
            public int read() throws IOException {
                try {new CountDownLatch(1).await();return -1;}catch(InterruptedException error) {Thread.currentThread().interrupt();throw new InterruptedIOException();}
            }
        };
        var process=helper(output,new ByteArrayOutputStream(),false);
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3),()->assertThatThrownBy(()->LocalModelKeyStore.exchange(process,new byte[32],50)).hasMessage("MODEL_LOCAL_KEY_UNAVAILABLE"));
    }
}
