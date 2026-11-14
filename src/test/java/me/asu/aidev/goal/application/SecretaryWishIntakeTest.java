package me.asu.aidev.goal.application;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.software.GoalProposals;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SecretaryWishIntakeTest {
    @Test void confirmedRequirementEntersOnlyItsOwnerWishPoolAndReplayIsReadOnly() {
        var connection=new SingleConnectionDataSource("jdbc:sqlite::memory:",true);
        try {
            var jdbc=new JdbcTemplate(connection);
            jdbc.execute("CREATE TABLE authority_owner(id TEXT PRIMARY KEY,actor TEXT)");
            jdbc.execute("CREATE TABLE goal_draft(id TEXT PRIMARY KEY,actor TEXT,title TEXT,revision INTEGER,epoch INTEGER,deleted BOOLEAN,payload TEXT,updated_at BIGINT)");
            jdbc.execute("CREATE TABLE selected_goal_proposal(owner_id TEXT,request_id TEXT,worker_id TEXT,projection_json TEXT,digest TEXT,instance_id TEXT,generation BIGINT,worker_boot TEXT,platform_boot TEXT,receipt_json TEXT,PRIMARY KEY(owner_id,request_id))");
            jdbc.update("INSERT INTO authority_owner VALUES('owner','human'),('other','another-human')");
            var db=new AuthorityRepository(jdbc);var wishes=new GoalRepository(jdbc);
            var boundary=mock(AuthorityBoundary.class);when(boundary.processBoot(anyString(),anyString())).thenReturn(true);
            when(boundary.instance()).thenReturn("instance");when(boundary.boot()).thenReturn("manager-boot");
            var intake=new GoalProposals(db,boundary,mock(ActionAdmissionService.class),wishes);
            var principal=new AuthorityBoundary.Principal("owner","secretary",1,true);
            String raw=db.encode(new GoalProposals.Projection("Build a form","Use the existing account system","Validate required fields"));
            var input=new GoalProposals.Proposal(UUID.randomUUID().toString(),raw,AuthorityRepository.hash(raw));
            @SuppressWarnings("unchecked") var receipt=(Map<String,Object>)intake.submit(principal,"worker-boot",input);
            String goal=(String)receipt.get("goalId");
            assertThat(wishes.get(goal,"human").content().body()).contains("existing account system","Validate required fields");
            assertThat(wishes.get(goal,"human").content().missing()).isNotBlank();
            assertThatThrownBy(()->wishes.get(goal,"another-human")).isInstanceOf(SecurityException.class);
            assertThat(intake.submit(principal,"worker-boot",input)).isEqualTo(receipt);
            assertThat(intake.receipt(principal,"worker-boot",input.requestId())).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_draft",Long.class)).isEqualTo(1);
            var wrongWorker=new AuthorityBoundary.Principal("owner","different-worker",1,true);
            assertThatThrownBy(()->intake.submit(wrongWorker,"worker-boot",input)).isInstanceOf(IllegalStateException.class);
            // A receipt recorded before this feature stays unchanged; querying never creates a wish.
            jdbc.update("UPDATE selected_goal_proposal SET receipt_json=? WHERE request_id=?",db.encode(Map.of("state","RECORDED","requestId",input.requestId())),input.requestId());
            assertThat(((Map<?,?>)intake.submit(principal,"worker-boot",input)).containsKey("goalId")).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_draft",Long.class)).isEqualTo(1);
        } finally {connection.destroy();}
    }
}
