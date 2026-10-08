package me.asu.aidev.autonomous.port;

import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEntry;
import java.util.List;

/** Read-model projection over existing authoritative tables plus append-only run events. */
public interface AutonomousRunTimelineRepository {
    List<AutonomousRunTimelineEntry> findProjectedEntries(String runId);
}
