package me.asu.aidev.workflow.application;

import java.time.Instant;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowPlanPurgeScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkflowPlanPurgeScheduler.class);
    private final JdbcWorkflowPlanArchiveRepository archives;

    public WorkflowPlanPurgeScheduler(JdbcWorkflowPlanArchiveRepository archives) { this.archives = archives; }

    @Scheduled(fixedDelayString = "${aidev.web-console.plan-purge.interval-ms:3600000}")
    public void purgeDue() {
        Instant now = Instant.now();
        for (String id : archives.dueIds(now)) {
            try { archives.purge(id, now); }
            catch (RuntimeException error) { log.error("Archived plan purge failed for {}; retrying later", id, error); }
        }
    }
}
