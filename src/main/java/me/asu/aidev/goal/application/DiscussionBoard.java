package me.asu.aidev.goal.application;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.goal.domain.GoalData.Draft;
import me.asu.aidev.goal.jdbc.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plane-owned topics and replies, scoped to the operator who owns the discussion. */
@Service
public class DiscussionBoard {
    public record Topic(String id,String wishId,String title,boolean closed,long updatedMs) {}
    public record Post(String id,String role,String body,long createdMs) {}
    public record Clarification(String questionId,String answerId,String secretaryId,String delegationId) {}
    public record Detail(Topic topic,List<Post> posts,Clarification clarification) {}
    public record SecretaryWork(String threadId,String title,String question,String delegationId) {}
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;

    public DiscussionBoard(AuthorityRepository repo,AuthorityBoundary boundary) { this.repo=repo;this.boundary=boundary; }

    public List<Topic> search(String actor,String query,String wish,int page) {
        if(page<0||page>1000||query.length()>160)throw new IllegalArgumentException("Invalid discussion search.");
        String match="%"+query.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return repo.jdbc().query("""
            SELECT t.* FROM discussion_thread t WHERE t.actor=? AND (?='' OR t.wish_id=?)
             AND (LOWER(t.title) LIKE LOWER(?) ESCAPE '!' OR EXISTS (
                 SELECT 1 FROM discussion_post p WHERE p.thread_id=t.id AND LOWER(p.body) LIKE LOWER(?) ESCAPE '!'))
             ORDER BY t.updated_ms DESC,t.id LIMIT 26 OFFSET ?
            """,this::topic,actor,wish,wish,match,match,25*page);
    }

    public Detail detail(String actor,String id) {
        var topic=find(actor,id);
        return read(topic);
    }

    private Detail read(Topic topic) {
        return new Detail(topic,repo.jdbc().query("""
            SELECT id,author_role,body,created_ms FROM discussion_post WHERE thread_id=?
             ORDER BY created_ms,id
            """,(rs,n)->new Post(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4)),topic.id()),clarification(topic.id()));
    }

    @Transactional public Topic create(String actor,String title,String body) {
        String id=UUID.randomUUID().toString();
        repo.jdbc().update("INSERT INTO discussion_thread(id,actor,title,updated_ms) VALUES(?,?,?,?)",
                id,actor,text(title,160),System.currentTimeMillis());
        add(actor,id,UUID.randomUUID().toString(),"USER",body);
        return find(actor,id);
    }

    @Transactional public void reply(String actor,String id,String body) {
        if(!add(actor,id,UUID.randomUUID().toString(),"USER",body))
            throw new IllegalStateException("This discussion is closed.");
    }

    @Transactional public void close(String actor,String id) {
        if(repo.jdbc().update("UPDATE discussion_thread SET closed=TRUE,updated_ms=? WHERE actor=? AND id=? AND closed=FALSE",
                System.currentTimeMillis(),actor,id)==0)find(actor,id);
    }

    @Transactional public void delegate(String actor,String id,String question) {
        var active=repo.jdbc().queryForList("""
            SELECT i.worker_id FROM authority_identity i JOIN authority_owner o ON o.id=i.owner_id
             WHERE o.actor=? AND i.identity_kind='AGENT' AND i.approval_state='APPROVED' AND i.enabled_boot=?
            """,String.class,actor,boundary.boot());
        if(active.size()!=1)throw new IllegalStateException("Enable exactly one paired secretary in Agent management.");
        lockOpen(actor,id);
        requirePending(id,question);
        repo.jdbc().update("""
            UPDATE discussion_clarification SET secretary_id=?,delegation_id=?
             WHERE thread_id=? AND question_id=? AND delegation_id IS NULL
            """,active.getFirst(),UUID.randomUUID().toString(),id,question);
    }

    @Transactional public void withdraw(String actor,String id,String question) {
        lockOpen(actor,id);
        requirePending(id,question);
        repo.jdbc().update("""
            UPDATE discussion_clarification SET secretary_id=NULL,delegation_id=NULL WHERE thread_id=? AND question_id=?
            """,id,question);
    }

    @Transactional public void answer(String actor,String id,String question,String body) {
        lockOpen(actor,id);
        requirePending(id,question);
        recordAnswer(actor,id,question,UUID.randomUUID().toString(),"USER",body);
    }

    public List<SecretaryWork> secretaryTopics(AuthorityBoundary.Principal principal) {
        return repo.jdbc().query("""
            SELECT t.id,t.title,p.body,c.delegation_id FROM discussion_thread t
              JOIN discussion_clarification c ON c.thread_id=t.id
              JOIN discussion_post p ON p.thread_id=c.thread_id AND p.id=c.question_id
             WHERE t.actor=? AND c.secretary_id=? AND t.closed=FALSE AND c.answer_id IS NULL
             ORDER BY t.updated_ms DESC,t.id LIMIT 50
            """,(rs,n)->new SecretaryWork(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),
                secretaryActor(principal),principal.worker());
    }

    public List<Topic> secretaryBrowse(AuthorityBoundary.Principal principal,String query,int page) {
        return search(secretaryActor(principal),query,"",page);
    }

    public Detail secretaryDetail(AuthorityBoundary.Principal principal,String id) {
        var detail=detail(secretaryActor(principal),id);
        var item=detail.clarification();
        if(item==null||principal.worker().equals(item.secretaryId()))return detail;
        return new Detail(detail.topic(),detail.posts(),new Clarification(item.questionId(),item.answerId(),null,null));
    }

    @Transactional public void secretaryReply(AuthorityBoundary.Principal principal,String id,UUID delegation,UUID requestId,String body) {
        String actor=secretaryActor(principal);
        if(delegation==null||requestId==null)throw new IllegalArgumentException("Missing reply request ID.");
        lockOpen(actor,id);
        var item=clarification(id);
        if(item==null||!principal.worker().equals(item.secretaryId())||!delegation.toString().equals(item.delegationId()))
            throw new NoSuchElementException();
        String answerId="secretary-"+delegation+"-"+requestId;
        if(answerId.equals(item.answerId())) {
            String saved=repo.jdbc().queryForObject("SELECT body FROM discussion_post WHERE thread_id=? AND id=?",String.class,id,answerId);
            if(!text(body,4000).equals(saved))throw new IllegalArgumentException("The reply request already has a different answer.");
            return;
        }
        requirePending(id,item.questionId());
        recordAnswer(actor,id,item.questionId(),answerId,"SECRETARY",body);
    }

    private void recordAnswer(String actor,String id,String question,String post,String role,String body) {
        add(actor,id,post,role,text(body,4000));
        repo.jdbc().update("UPDATE discussion_clarification SET answer_id=? WHERE thread_id=? AND question_id=?",
                post,id,question);
    }

    private void lockOpen(String actor,String id) {
        if(repo.jdbc().update("UPDATE discussion_thread SET updated_ms=updated_ms WHERE actor=? AND id=? AND closed=FALSE",
                actor,id)!=1)throw new IllegalStateException("Discussion is unavailable or closed.");
    }

    private Clarification clarification(String id) {
        return repo.jdbc().query("""
            SELECT c.question_id,c.answer_id,c.secretary_id,c.delegation_id FROM discussion_clarification c
              JOIN discussion_post p ON p.thread_id=c.thread_id AND p.id=c.question_id
             WHERE c.thread_id=? ORDER BY p.created_ms DESC,p.id DESC LIMIT 1
            """,(rs,n)->new Clarification(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),id)
                .stream().findFirst().orElse(null);
    }

    private void requirePending(String id,String question) {
        var item=clarification(id);
        if(item==null||!item.questionId().equals(question)||item.answerId()!=null)
            throw new IllegalStateException("The clarification changed. Reload the discussion.");
    }

    private String secretaryActor(AuthorityBoundary.Principal principal) {
        if(principal==null||!principal.enabled())throw new SecurityException("An enabled secretary is required.");
        return repo.actor(principal.owner());
    }

    @Transactional public void managerClarification(GoalRepository goals,Draft wish,String advice,String knownAnswers) {
        goals.lock(wish);
        repo.jdbc().update("""
            INSERT INTO discussion_thread(id,actor,wish_id,title,updated_ms) VALUES(?,?,?,?,?)
             ON CONFLICT (actor,wish_id) DO NOTHING
            """,UUID.randomUUID().toString(),wish.actor(),wish.id(),wish.title(),System.currentTimeMillis());
        for(String id:wishTopics(wish.actor(),wish.id())) {
            if(find(wish.actor(),id).closed())continue;
            lockOpen(wish.actor(),id);
            if(!answers(wish.actor(),wish.id()).equals(knownAnswers))
                throw new IllegalStateException("A clarification was answered during review. Submit the wish again.");
            String question="manager-"+wish.revision();
            var previous=clarification(id);
            if(previous!=null&&previous.answerId()==null
                    &&(previous.questionId().equals(question)||previous.questionId().startsWith(question+"-")))continue;
            if(previous!=null&&previous.answerId()!=null)
                question+="-"+AuthorityRepository.hash(previous.answerId()).substring(0,16);
            if(!add(wish.actor(),id,question,"MANAGER",advice))continue;
            if(repo.jdbc().update("INSERT INTO discussion_clarification(thread_id,question_id) VALUES(?,?) ON CONFLICT DO NOTHING",id,question)==1)
                repo.jdbc().update("""
                    UPDATE discussion_clarification SET secretary_id=NULL,delegation_id=NULL
                     WHERE thread_id=? AND question_id<>? AND answer_id IS NULL
                    """,id,question);
        }
    }

    public String answers(String actor,String wish) {
        var replies=repo.jdbc().query("""
            SELECT q.body,a.body FROM discussion_thread t JOIN discussion_clarification c ON c.thread_id=t.id
              JOIN discussion_post q ON q.thread_id=c.thread_id AND q.id=c.question_id
              JOIN discussion_post a ON a.thread_id=c.thread_id AND a.id=c.answer_id
             WHERE t.actor=? AND t.wish_id=? ORDER BY a.created_ms DESC,a.id DESC LIMIT 3
            """,(rs,n)->"Question:\n"+rs.getString(1)+"\nAnswer:\n"+rs.getString(2),actor,wish);
        if(replies.isEmpty())return "";
        String context=replies.getFirst();
        for(String older:replies.subList(1,replies.size())) {
            if(context.length()+older.length()+2>8000)break;
            context=older+"\n\n"+context;
        }
        return context;
    }

    @Transactional public void employeeReply(String actor,String wish,String request,String body) {
        for(String id:wishTopics(actor,wish))add(actor,id,request,"EMPLOYEE",body);
    }

    private List<String> wishTopics(String actor,String wish) {
        return repo.jdbc().queryForList("SELECT id FROM discussion_thread WHERE actor=? AND wish_id=?",String.class,actor,wish);
    }

    private boolean add(String actor,String id,String post,String role,String body) {
        long now=System.currentTimeMillis();
        String content=text(body,16000);
        if(repo.jdbc().update("""
            UPDATE discussion_thread SET updated_ms=? WHERE id=? AND actor=? AND closed=FALSE
            """,now,id,actor)==0){find(actor,id);return false;}
        repo.jdbc().update("""
            INSERT INTO discussion_post(thread_id,id,author_role,body,created_ms) VALUES(?,?,?,?,?)
             ON CONFLICT (thread_id,id) DO NOTHING
            """,id,post,role,content,now);
        return true;
    }

    private Topic find(String actor,String id) {
        return repo.jdbc().query("SELECT * FROM discussion_thread WHERE actor=? AND id=?",this::topic,actor,id)
                .stream().findFirst().orElseThrow(NoSuchElementException::new);
    }

    private Topic topic(java.sql.ResultSet rs,int number) throws java.sql.SQLException {
        return new Topic(rs.getString("id"),rs.getString("wish_id"),rs.getString("title"),
                rs.getBoolean("closed"),rs.getLong("updated_ms"));
    }

    private static String text(String value,int limit) {
        if(value==null||value.isBlank()||value.length()>limit||value.indexOf(0)>=0)
            throw new IllegalArgumentException("Enter valid discussion content.");
        return value.strip();
    }
}
