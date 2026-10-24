package me.asu.aidev.execution.config;

import java.util.List;
import me.asu.aidev.execution.guard.GuardContract.Lease;

/** Supplemental task data and observed progress, never a replacement for task or tool authority. */
public final class WorkbenchContract {
    public static final String PROTOCOL="aiw.worker.workbench.v1";
    private WorkbenchContract() {}
    public record Query(String taskId,String attemptId) {}
    public record ReceiptQuery(String requestId) {}
    public record BranchQuery(String taskId,String nodeId) {}
    public record HumanCommand(String requestId,String action,String taskId,String attemptId,int instructionRevision,
                               String mode,String text,String messageId,String parentId,String title,String resultRevision) {}
    public record MachineCommand(Lease lease,String action,String batchId,String mode,String operationId,String batchDigest) {}
    public record Input(String id,String mode,String text,String digest,long sequence) {}
    public record Batch(String protocol,String phase,String batchId,String digest,String state,List<Input> messages) {}
}
