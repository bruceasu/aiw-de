package me.asu.aidev.execution.port;

import me.asu.aidev.work.domain.WorkTask;

/** Additional authority checked before an already-leased task may continue. */
public interface TaskExecutionPolicy { void assertExecutionAllowed(WorkTask task); }
