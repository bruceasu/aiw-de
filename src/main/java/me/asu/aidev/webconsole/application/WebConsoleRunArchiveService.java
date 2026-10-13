package me.asu.aidev.webconsole.application;

import java.time.Instant;
import java.time.Duration;
import java.util.NoSuchElementException;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleRunArchiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archives only finished Runs from the console's default list. */
@Service
public class WebConsoleRunArchiveService {
    private static final Duration DELETE_DELAY = Duration.ofDays(30);
    private final AutonomousRunRepository runs;
    private final JdbcWebConsoleRunArchiveRepository archives;

    public WebConsoleRunArchiveService(AutonomousRunRepository runs,
                                       JdbcWebConsoleRunArchiveRepository archives) {
        this.runs = runs;
        this.archives = archives;
    }

    @Transactional
    public void archive(String runId, String actor) {
        var run = runs.find(runId)
                .orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + runId));
        if (!run.terminal() || run.activeSlots() != 0) {
            throw new IllegalStateException("only terminal Runs without active slots can be archived");
        }
        if (!archives.archive(runId, actor, Instant.now())) {
            throw new IllegalStateException("Run is already archived or its state changed");
        }
    }

    @Transactional
    public void scheduleDeletion(String runId, String actor) {
        Instant now = Instant.now();
        if (!archives.scheduleDeletion(runId, actor, now, now.plus(DELETE_DELAY))) {
            throw new IllegalStateException("Run must be archived and not already scheduled for deletion");
        }
    }

    @Transactional
    public void undoDeletion(String runId) {
        if (!archives.undoDeletion(runId, Instant.now())) {
            throw new IllegalStateException("Run has no pending deletion or its 30-day period has expired");
        }
    }
}
