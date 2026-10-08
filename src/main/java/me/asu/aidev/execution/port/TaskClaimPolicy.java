package me.asu.aidev.execution.port;

import me.asu.aidev.work.domain.WorkTask;

/** Additional authority checked before a READY task may be leased. */
public interface TaskClaimPolicy { boolean canClaim(WorkTask task); }
