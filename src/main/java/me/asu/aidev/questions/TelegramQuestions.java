package me.asu.aidev.questions;

import java.security.SecureRandom;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import me.asu.aidev.authority.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.questions.QuestionContract.*;

/** Explicit human binding, short-lived correlation and durable single-send intent. No transport grants. */
@Service
public class TelegramQuestions {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final FormalQuestions questions;
    private final SecureRandom random=new SecureRandom();
    public TelegramQuestions(AuthorityRepository repo,AuthorityBoundary boundary,FormalQuestions questions) {this.repo=repo;this.boundary=boundary;this.questions=questions;}
    public record Send(String id,String chatId,String text,List<String> options,String nonce) {}
    @Transactional public Object manage(String plan,String operation,Binding input,String questionId) {
        boundary.lock();String owner=questions.owner(plan);
        questions.readHuman(owner); // Binding this owner's private channel grants no business action.
        var previous=repo.jdbc().queryForList("SELECT * FROM question_telegram_binding WHERE owner_id=?",owner);
        if(operation.equals("read")) return Map.of("bindings",previous,"deliveries",repo.jdbc().queryForList("SELECT id,question_id,state,message_id,expires_ms FROM question_telegram_outbox WHERE owner_id=? ORDER BY expires_ms DESC LIMIT 100",owner));
        if(Set.of("bind","unbind").contains(operation)) {
            if(operation.equals("bind")&&input==null) throw new IllegalArgumentException("Explicit private binding required");
            long generation=previous.isEmpty()?1:((Number)previous.getFirst().get("generation")).longValue()+1;
            var b=input==null?new Binding(String.valueOf(previous.getFirst().get("user_id")),String.valueOf(previous.getFirst().get("chat_id"))):input;
            repo.jdbc().update("INSERT INTO question_telegram_binding VALUES(?,?,?,?,?,?) ON CONFLICT(owner_id) DO UPDATE SET actor=excluded.actor,user_id=excluded.user_id,chat_id=excluded.chat_id,generation=excluded.generation,enabled=excluded.enabled",owner,boundary.human(),b.userId(),b.privateChatId(),generation,operation.equals("bind"));
            repo.jdbc().update("UPDATE question_telegram_outbox SET state='INVALIDATED' WHERE owner_id=? AND state<>'INVALIDATED'",owner);
            repo.audit("PLAN:"+plan,null,null,boundary.human(),"QUESTION_TELEGRAM_"+operation.toUpperCase(Locale.ROOT),"bindingGeneration="+generation);return Map.of("state",operation,"generation",generation);
        }
        if(!operation.equals("send")) throw new IllegalArgumentException("Unknown Telegram operation");
        var q=questions.get(questionId);if(!owner.equals(q.ownerId())||q.state()!=State.OPEN) throw new IllegalStateException("Current open question required");
        questions.human(owner,q.kind()==Kind.AUTHORIZATION?"A18_BUDGET":"A19_ANSWER");
        var b=binding(owner);String id="QT-"+UUID.randomUUID();
        // Explicit reissue supersedes old correlation; never resends a SENDING/UNKNOWN operation.
        repo.jdbc().update("UPDATE question_telegram_outbox SET state='INVALIDATED' WHERE question_id=? AND state IN ('READY','DELIVERED')",questionId);
        repo.jdbc().update("INSERT INTO question_telegram_outbox VALUES(?,?,?,?,?,?,?,'READY',NULL)",id,owner,questionId,q.requestVersion(),b.get("generation"),AuthorityRepository.hash(id),System.currentTimeMillis()+900_000);
        return Map.of("deliveryId",id,"state","READY");
    }
    private Map<String,Object> binding(String owner) {var b=repo.jdbc().queryForMap("SELECT * FROM question_telegram_binding WHERE owner_id=?",owner);if(!AuthorityRepository.flag(b.get("enabled"))) throw new SecurityException("Current Telegram binding required");return b;}
    @Transactional public Send prepare(String id) {
        boundary.lock();var r=repo.jdbc().queryForMap("SELECT * FROM question_telegram_outbox WHERE id=?",id);
        if(!r.get("state").equals("READY")) return null;
        var b=binding(String.valueOf(r.get("owner_id")));var q=questions.get(String.valueOf(r.get("question_id")));
        if(!current(r,b,q)) {invalidate(id);return null;}
        byte[] bytes=new byte[24];random.nextBytes(bytes);String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if(repo.jdbc().update("UPDATE question_telegram_outbox SET state='SENDING',token_digest=? WHERE id=? AND state='READY'",AuthorityRepository.hash(nonce),id)!=1) return null;
        String text="AIW question "+q.questionId()+" v"+q.requestVersion()+"\n"+q.question()+"\n"+q.context()+"\n"+q.answerRequirements()+"\nOptions: "+repo.encode(q.options());
        // No truncation of an authorization scope. Oversize content stays inspectable in Console.
        if(text.length()>3500) {invalidate(id);return null;}
        return new Send(id,String.valueOf(b.get("chat_id")),text+"\nReply to this message. This question does not grant other actions.",q.options(),nonce);
    }
    private boolean current(Map<String,Object> r,Map<String,Object> b,Question q) {
        return AuthorityRepository.flag(b.get("enabled"))&&Objects.equals(r.get("binding_generation"),b.get("generation"))&&((Number)r.get("expires_ms")).longValue()>System.currentTimeMillis()
                &&((Number)r.get("request_version")).longValue()==q.requestVersion()&&q.state()==State.OPEN;
    }
    private void invalidate(String id) {repo.jdbc().update("UPDATE question_telegram_outbox SET state='INVALIDATED' WHERE id=?",id);}
    @Transactional public void sent(String id,String messageId) {
        boundary.lock();var r=repo.jdbc().queryForMap("SELECT * FROM question_telegram_outbox WHERE id=?",id);var b=binding(String.valueOf(r.get("owner_id")));var q=questions.get(String.valueOf(r.get("question_id")));
        if(!current(r,b,q)) {invalidate(id);return;}
        repo.jdbc().update("UPDATE question_telegram_outbox SET state='DELIVERED',message_id=? WHERE id=? AND state='SENDING'",messageId,id);
    }
    @Transactional public void unknown(String id) {boundary.lock();repo.jdbc().update("UPDATE question_telegram_outbox SET state='UNKNOWN' WHERE id=? AND state='SENDING'",id);}
    @Transactional public void recoverSending() {boundary.lock();repo.jdbc().update("UPDATE question_telegram_outbox SET state='UNKNOWN' WHERE state='SENDING'");}
    /** Minimal selected inbound data only. Token is hashed before persistence. */
    public Map<String,Object> project(JsonNode update) {
        var callback=update.path("callback_query");var message=callback.isMissingNode()?update.path("message"):callback.path("message");
        String data=callback.path("data").asText("");boolean question=data.startsWith("aiw:q:");
        if(!question && !callback.isMissingNode()) return data.startsWith("aiw:a:")||data.startsWith("aiw:r:")?Map.of("kind","LEGACY","callback",repo.decode(callback.toString(),Object.class)):Map.of("kind","IGNORE");
        String chat=message.path("chat").path("id").asText(""),user=(callback.isMissingNode()?message:callback).path("from").path("id").asText("");
        if(!message.path("chat").path("type").asText("").equals("private")||repo.jdbc().queryForObject("SELECT COUNT(*) FROM question_telegram_binding WHERE chat_id=? AND user_id=? AND enabled=TRUE",Long.class,chat,user)!=1) return Map.of("kind","IGNORE");
        var p=new LinkedHashMap<String,Object>();p.put("kind","QUESTION");p.put("chat",chat);p.put("user",user);p.put("message",message.path("message_id").asText(""));
        if(question) {
            String[] pieces=data.split(":",-1);if(pieces.length!=4||!pieces[2].matches("[A-Za-z0-9_-]{32}")||!pieces[3].matches("[0-9]{1,2}")) return Map.of("kind","IGNORE");
            p.put("tokenDigest",AuthorityRepository.hash(pieces[2]));p.put("option",Integer.parseInt(pieces[3]));p.put("identity","callback:"+callback.path("id").asText(""));
        } else {
            String reply=message.path("reply_to_message").path("message_id").asText("");String text=message.path("text").asText("");
            if(reply.isEmpty()||text.isBlank()||text.length()>8000||repo.jdbc().queryForObject("SELECT COUNT(*) FROM question_telegram_outbox o JOIN question_telegram_binding b ON b.owner_id=o.owner_id WHERE b.chat_id=? AND b.user_id=? AND b.generation=o.binding_generation AND o.message_id=? AND o.state='DELIVERED'",Long.class,chat,user,reply)!=1) return Map.of("kind","IGNORE");
            p.put("replyTo",reply);p.put("text",text);p.put("identity","message:"+chat+":"+p.get("message"));
        }return p;
    }
    @Transactional public void receive(long updateId,Map<String,Object> p) {
        boundary.lock();String identity=String.valueOf(p.get("identity"));
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM question_telegram_inbox WHERE update_id=? OR identity_key=?",Long.class,updateId,identity)>0) return;
        var b=repo.jdbc().queryForMap("SELECT * FROM question_telegram_binding WHERE chat_id=? AND user_id=? AND enabled=TRUE",p.get("chat"),p.get("user"));
        String owner=String.valueOf(b.get("owner_id"));var rows=p.containsKey("tokenDigest")?
                repo.jdbc().queryForList("SELECT * FROM question_telegram_outbox WHERE owner_id=? AND token_digest=? AND message_id=? AND state='DELIVERED'",owner,p.get("tokenDigest"),p.get("message")):
                repo.jdbc().queryForList("SELECT * FROM question_telegram_outbox WHERE owner_id=? AND message_id=? AND state='DELIVERED'",owner,p.get("replyTo"));
        if(rows.size()!=1) throw new SecurityException("Unknown question correlation");var r=rows.getFirst();var q=questions.get(String.valueOf(r.get("question_id")));
        if(!current(r,b,q)) throw new SecurityException("Expired question or binding");
        String raw=p.containsKey("option")?q.options().get(((Number)p.get("option")).intValue()):String.valueOf(p.get("text"));
        if(q.kind()==Kind.AUTHORIZATION&&!p.containsKey("option")) throw new SecurityException("Authorization requires its exact scoped button");
        String previous=boundary.human();boundary.human(String.valueOf(b.get("actor")));
        try {questions.human(owner,q.kind()==Kind.AUTHORIZATION?"A18_BUDGET":"A19_ANSWER");questions.answer(new Reply(q.questionId(),q.requestVersion(),raw,identity,null,null),owner,"telegram:user:"+p.get("user"),"TELEGRAM",false);}
        finally {boundary.human(previous);}
        repo.jdbc().update("INSERT INTO question_telegram_inbox VALUES(?,?,?,'ACCEPTED')",updateId,identity,System.currentTimeMillis());
    }
}
