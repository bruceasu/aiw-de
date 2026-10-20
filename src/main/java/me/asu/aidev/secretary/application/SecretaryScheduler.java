package me.asu.aidev.secretary.application;

import java.time.Instant;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aidev.secretary.enabled",havingValue="true",matchIfMissing=true)
public class SecretaryScheduler {
    private static final Logger log=LoggerFactory.getLogger(SecretaryScheduler.class);
    private final SecretaryRepository repo;
    private final SecretaryService service;
    public SecretaryScheduler(SecretaryRepository repo,SecretaryService service) { this.repo=repo;this.service=service; }
    @Scheduled(fixedDelayString="${aidev.secretary.interval-ms:5000}")
    public void scan() {
        Instant now=Instant.now();
        for(String id:repo.due(now.toEpochMilli())) {
            try { service.tick(id); }
            catch(RuntimeException e) { log.error("Secretary arrangement {} could not be admitted",id,e); }
        }
    }
}
