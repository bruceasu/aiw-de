package me.asu.aidev.questions;

import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries database consumption of retained answers. Never replays an external operation. */
@Component
public class QuestionRecovery {
    private final AuthorityRepository repo;private final FormalQuestions questions;
    public QuestionRecovery(AuthorityRepository repo,FormalQuestions questions) {this.repo=repo;this.questions=questions;}
    @Scheduled(fixedDelayString="${aidev.questions.recovery-ms:5000}") public void recover() {
        for(String id:repo.jdbc().queryForList("SELECT id FROM formal_question WHERE state='ANSWERED' ORDER BY created_ms LIMIT 64",String.class)) {
            try {questions.consume(id);}catch(RuntimeException error) {repo.jdbc().update("UPDATE formal_question SET blocked_reason='CONSUMPTION_REJECTED_SOURCE_OR_CONTROL' WHERE id=? AND state='ANSWERED'",id);}
        }
    }
}
