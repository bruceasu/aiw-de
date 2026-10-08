package me.asu.aidev.autonomous.port;

import me.asu.aidev.autonomous.domain.AutonomousRunEvent;
import me.asu.aidev.autonomous.domain.AutonomousRunEventType;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.List;

public interface AutonomousRunEventRepository {
    AutonomousRunEvent append(String runId, AutonomousRunEventType type, String actor,
                              TaskId relatedTaskId, String detail, Instant occurredAt);
    List<AutonomousRunEvent> findByRun(String runId);
}
