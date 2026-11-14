package me.asu.aidev.authority;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import static org.assertj.core.api.Assertions.*;

class WorkerAccessTest {
    @TempDir Path directory;
    @Test void boundCredentialsAreHashedAndRevocableWithoutLosingIdentity(){
        var data=new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("worker.db"));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V49__worker_access_credentials.sql")).execute(data);
        var jdbc=new JdbcTemplate(data);var access=new WorkerAccess(jdbc);
        var input=new WorkerAccess.Create(UUID.randomUUID().toString(),"remote-coder","Development laptop");
        var issued=access.create("web:admin",input);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM worker_access_credential",String.class)).doesNotContain(issued.token());
        assertThat(access.authenticate(issued.token(),true)).isEqualTo("remote-coder");
        assertThat(access.create("web:admin",input).token()).isNull();
        var draining=access.change(issued.credential().id(),new WorkerAccess.Change(1,"DRAINING"));
        assertThat(access.authenticate(issued.token(),false)).isEqualTo("remote-coder");
        assertThatThrownBy(()->access.authenticate(issued.token(),true)).isInstanceOf(IllegalStateException.class);
        access.change(draining.id(),new WorkerAccess.Change(draining.revision(),"REVOKED"));
        assertThatThrownBy(()->access.authenticate(issued.token(),false)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->access.authenticate("",false)).isInstanceOf(SecurityException.class);
        var boundary=new AuthorityBoundary(null);boundary.workerIdentity("remote-coder");
        assertThatThrownBy(()->boundary.requireWorker("another-worker",false)).isInstanceOf(SecurityException.class);
        boundary.workerIdentity(null);
        assertThatThrownBy(()->boundary.requireWorker("remote-coder",true)).isInstanceOf(SecurityException.class);
    }
    @Test void configurationRegistrationRejectsAnotherWorkersIdentityBeforeWriting(){
        var boundary=new AuthorityBoundary(null);boundary.workerIdentity("worker-a");
        var configurations=new me.asu.aidev.execution.config.ExecutionConfigurations(new AuthorityRepository(null),boundary,null,null);
        var capabilities=org.mockito.Mockito.mock(me.asu.aidev.execution.config.ConfigurationContract.WorkerCapabilities.class);
        assertThatThrownBy(()->configurations.registerWorker(new me.asu.aidev.execution.config.ConfigurationContract.WorkerRegistration("worker-b",capabilities))).isInstanceOf(SecurityException.class);
    }
    @Test void localTrustRequiresAnExplicitLoopbackDeploymentAndUnforwardedLocalRequest(){
        var security=new WebConsoleSecurityService("admin","");ReflectionTestUtils.setField(security,"bindAddress","127.0.0.1");
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.setServerName("localhost");
        assertThat(security.localRequest(request)).isTrue();security.localLogin(request,request.getSession());
        assertThat(security.actor(request.getSession())).isEqualTo("web:admin");
        request.addHeader("X-Forwarded-For","203.0.113.1");assertThat(security.localRequest(request)).isFalse();
        request.removeHeader("X-Forwarded-For");request.setServerName("untrusted.example");assertThat(security.localRequest(request)).isFalse();
        request.setServerName("localhost");ReflectionTestUtils.setField(security,"remoteAccess",true);
        assertThat(security.localRequest(request)).isFalse();assertThatThrownBy(security::validateDeployment).isInstanceOf(IllegalStateException.class);
    }
}
