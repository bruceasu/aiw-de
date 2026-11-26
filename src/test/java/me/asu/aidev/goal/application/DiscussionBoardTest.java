package me.asu.aidev.goal.application;

import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;

class DiscussionBoardTest {
    @TempDir Path temp;

    @Test void secretaryReadsOwnerTopicsAndCanAnswerOnlyAnActiveClarificationDelegation() {
        var source=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("discussion.db"));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__discussion_board.sql"),
                new ClassPathResource("db/migration/V7__discussion_clarifications.sql")).execute(source);
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE authority_owner(id TEXT,actor TEXT)");
        jdbc.execute("CREATE TABLE authority_identity(owner_id TEXT,worker_id TEXT,identity_kind TEXT,approval_state TEXT,enabled_boot TEXT)");
        jdbc.update("INSERT INTO authority_owner VALUES('alice','web:alice'),('bob','web:bob')");
        var repository=new AuthorityRepository(jdbc);
        var boundary=new AuthorityBoundary(repository);
        jdbc.update("INSERT INTO authority_identity VALUES('alice','assistant','AGENT','APPROVED',?),('bob','other','AGENT','APPROVED',?)",
                boundary.boot(),boundary.boot());
        var board=new DiscussionBoard(repository,boundary);
        var secretary=new AuthorityBoundary.Principal("alice","assistant",1,true);
        var outsider=new AuthorityBoundary.Principal("bob","other",1,true);
        var topic=board.create("web:alice","Documents","Check the refund policy");
        assertThat(board.search("web:alice","refund","",0)).extracting(DiscussionBoard.Topic::id).containsExactly(topic.id());
        assertThat(board.search("web:bob","refund","",0)).isEmpty();
        assertThat(board.secretaryBrowse(secretary,"refund",0)).extracting(DiscussionBoard.Topic::id).containsExactly(topic.id());
        assertThat(board.secretaryDetail(secretary,topic.id()).topic().title()).isEqualTo("Documents");
        assertThat(board.secretaryTopics(secretary)).isEmpty();
        assertThatThrownBy(()->board.secretaryDetail(outsider,topic.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(()->board.delegate("web:alice",topic.id(),"manager-2")).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE discussion_thread SET wish_id='wish' WHERE id=?",topic.id());
        jdbc.update("INSERT INTO discussion_post VALUES(?, 'manager-2','MANAGER','What is the refund period?',?)",
                topic.id(),System.currentTimeMillis());
        jdbc.update("INSERT INTO discussion_clarification(thread_id,question_id) VALUES(?,'manager-2')",topic.id());
        board.delegate("web:alice",topic.id(),"manager-2");
        var withdrawn=UUID.fromString(board.secretaryTopics(secretary).getFirst().delegationId());
        board.withdraw("web:alice",topic.id(),"manager-2");
        assertThat(board.secretaryTopics(secretary)).isEmpty();
        board.delegate("web:alice",topic.id(),"manager-2");
        var pending=board.secretaryTopics(secretary).getFirst();
        assertThat(pending.question()).isEqualTo("What is the refund period?");
        var delegation=UUID.fromString(pending.delegationId());
        var request=UUID.randomUUID();
        assertThatThrownBy(()->board.secretaryReply(secretary,topic.id(),withdrawn,request,"Stale answer"))
                .isInstanceOf(NoSuchElementException.class);
        board.secretaryReply(secretary,topic.id(),delegation,request,"Thirty days");
        board.secretaryReply(secretary,topic.id(),delegation,request,"Thirty days");
        assertThatThrownBy(()->board.secretaryReply(secretary,topic.id(),delegation,request,"Sixty days"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(board.detail("web:alice",topic.id()).posts()).extracting(DiscussionBoard.Post::role)
                .containsExactlyInAnyOrder("USER","MANAGER","SECRETARY");
        assertThat(board.answers("web:alice","wish")).contains("What is the refund period?","Thirty days");
        assertThat(board.secretaryTopics(secretary)).isEmpty();
        board.close("web:alice",topic.id());
        assertThatThrownBy(()->board.reply("web:alice",topic.id(),"Late reply")).isInstanceOf(IllegalStateException.class);
    }
}
