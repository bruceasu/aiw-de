package me.asu.aidev.approval.telegram;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.databind.JsonNode;
import me.asu.aidev.authority.*;
import me.asu.aidev.questions.TelegramQuestions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One configured bot receiver. Persist a minimal inbox before acknowledging the Telegram offset. */
@Component
@ConditionalOnProperty(name="aidev.questions.telegram.enabled",havingValue="true")
public class QuestionTelegramTransport implements SmartLifecycle {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final TelegramQuestions questions;
    private final ObjectProvider<TelegramApprovalAdapter> legacy;private final TelegramBotApiClient client;private final TransactionTemplate tx;
    private final String bot;private final boolean legacyPolling;private final AtomicBoolean running=new AtomicBoolean();
    private FileChannel lockChannel;private FileLock lock;private Thread poller;
    public QuestionTelegramTransport(AuthorityRepository repo,AuthorityBoundary boundary,TelegramQuestions questions,ObjectProvider<TelegramApprovalAdapter> legacy,
            PlatformTransactionManager manager,@Value("${aidev.approval.telegram.token:}") String token,
            @Value("${aidev.approval.telegram.api-base:https://api.telegram.org}") String base,@Value("${aidev.approval.telegram.enabled:false}") boolean legacyPolling) {
        if(token.isBlank()) throw new IllegalArgumentException("Question Telegram requires an explicitly configured bot token");
        this.repo=repo;this.boundary=boundary;this.questions=questions;this.legacy=legacy;this.tx=new TransactionTemplate(manager);this.legacyPolling=legacyPolling;
        this.client=new TelegramBotApiClient(base,token);this.bot=AuthorityRepository.hash(token);
    }
    public long offset() {return repo.jdbc().queryForObject("SELECT next_offset FROM question_telegram_poll WHERE bot_id=?",Long.class,bot);}
    public void accept(JsonNode update) {
        long id=update.path("update_id").asLong(-1);if(id<0) throw new IllegalArgumentException("Telegram update identity required");
        var payload=questions.project(update);
        tx.executeWithoutResult(s->{boundary.lock();repo.jdbc().update("INSERT INTO question_telegram_update VALUES(?,?,?,'READY') ON CONFLICT(bot_id,update_id) DO NOTHING",bot,id,repo.encode(payload));
            repo.jdbc().update("UPDATE question_telegram_poll SET next_offset=CASE WHEN next_offset<? THEN ? ELSE next_offset END WHERE bot_id=?",id+1,id+1,bot);});
    }
    @Scheduled(fixedDelayString="${aidev.questions.telegram.dispatch-ms:2000}") public void dispatch() {
        if(!running.get()) return;
        for(var r:repo.jdbc().queryForList("SELECT update_id,payload_json FROM question_telegram_update WHERE bot_id=? AND state='READY' ORDER BY update_id LIMIT 64",bot)) {
            String outcome="APPLIED";long id=((Number)r.get("update_id")).longValue();
            try {
                @SuppressWarnings("unchecked") var p=(Map<String,Object>)repo.decode(String.valueOf(r.get("payload_json")),Map.class);
                if("QUESTION".equals(p.get("kind"))) questions.receive(id,p);
                else if("LEGACY".equals(p.get("kind"))) {var adapter=legacy.getIfAvailable();if(adapter!=null) adapter.handleRecordedCallback(repo.encode(p.get("callback")));}
            }catch(RuntimeException error) {outcome="REJECTED";}
            repo.jdbc().update("UPDATE question_telegram_update SET state=? WHERE bot_id=? AND update_id=? AND state='READY'",outcome,bot,id);
        }
        for(String id:repo.jdbc().queryForList("SELECT id FROM question_telegram_outbox WHERE state='READY' ORDER BY expires_ms LIMIT 16",String.class)) {
            TelegramQuestions.Send send;
            try {send=questions.prepare(id);}catch(RuntimeException error) {continue;}
            if(send==null) continue;
            try {questions.sent(id,client.sendQuestion(send.chatId(),send.text(),send.options(),send.nonce()));}
            catch(Exception error) {questions.unknown(id);if(error instanceof InterruptedException) Thread.currentThread().interrupt();}
        }
    }
    @Override public void start() {
        if(running.get()) return;
        try {
            var path=Path.of(System.getProperty("java.io.tmpdir"),"aiw-telegram-"+bot+".lock");
            lockChannel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);lock=lockChannel.tryLock();
            if(lock==null) throw new IllegalStateException("Only one local AIW consumer per Telegram bot is allowed");
            tx.executeWithoutResult(s->{boundary.lock();repo.jdbc().update("INSERT INTO question_telegram_poll VALUES(?,0) ON CONFLICT(bot_id) DO NOTHING",bot);});
            questions.recoverSending();running.set(true);
            if(!legacyPolling) poller=Thread.ofVirtual().name("aiw-question-telegram").start(()->{
                while(running.get()) {
                    try {for(JsonNode update:client.getUpdates(offset(),30).path("result")) accept(update);}
                    catch(InterruptedException error) {Thread.currentThread().interrupt();break;}
                    catch(Exception error) {try {Thread.sleep(1000);}catch(InterruptedException interrupted) {Thread.currentThread().interrupt();break;}}
                }
            });
        }catch(Exception error) {stop();throw new IllegalStateException("Question Telegram receiver ownership unavailable",error);}
    }
    @Override public void stop() {running.set(false);if(poller!=null) poller.interrupt();try {if(lock!=null) lock.release();if(lockChannel!=null) lockChannel.close();}catch(java.io.IOException ignored) {}}
    @Override public boolean isRunning() {return running.get();}
    @Override public boolean isAutoStartup() {return true;}
    @Override public int getPhase() {return Integer.MAX_VALUE-101;}
}
