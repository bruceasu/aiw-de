package me.asu.aidev.workflow.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only execution history for a plan task, excluding lease credentials. */
@Repository
public class JdbcWorkflowTaskReadRepository {
    public record Attempt(String id, int attemptNo, int instructionRevision, String workerId,
                          String state, Instant createdAt, Instant startedAt, Instant finishedAt,
                          String failureCategory, String failureCode, String verdict,
                          String report, String resultRevision) {
        @com.fasterxml.jackson.annotation.JsonProperty("diagnostic")
        public Diagnostic diagnostic() { return explain(failureCode); }
    }
    public record Diagnostic(String code,String summary,String nextAction,boolean specific) {}
    public static Diagnostic explain(String code) {
        if(code==null) return null;
        if(code.matches("MODEL_API_HTTP_[1-5][0-9]{2}")) {
            int status=Integer.parseInt(code.substring("MODEL_API_HTTP_".length()));
            if(status==401) return new Diagnostic(code,"The model API rejected authentication.",
                    "Check the configured credential and confirm the account can use this model.",true);
            if(status==403) return new Diagnostic(code,"The model API denied this request.",
                    "Check model access and provider account permissions.",true);
            if(status==400||status==422) return new Diagnostic(code,"The model API rejected the request parameters.",
                    "Check the configured model, API protocol, and supported request settings.",true);
            if(status==404) return new Diagnostic(code,"The model endpoint or requested model was not found.",
                    "Check the API endpoint and configured model name.",true);
            if(status==408||status==504) return new Diagnostic(code,"The model API request timed out.",
                    "Check the Worker network path and provider response time before retrying.",true);
            if(status==429) return new Diagnostic(code,"The model API rate limit was reached.",
                    "Wait for the provider limit to reset or select an available model profile.",true);
            if(status>=500) return new Diagnostic(code,"The model API returned a server error.",
                    "Check the provider service status before retrying.",true);
            return new Diagnostic(code,"The model API returned an HTTP error.",
                    "Check the provider request and model connection settings.",true);
        }
        if(code.matches("MODEL_EXCEPTION_[A-Z0-9_]{1,40}"))
            return new Diagnostic(code,"The model runtime raised a classified exception. See the error code for its type; raw error text was omitted.",
                    "Check the Worker runtime and model configuration. Raw exception text is omitted to protect request data.",true);
        String summary=switch(code) {
            case "WORKER_LEASE_EXPIRED" -> "The Worker stopped reporting before the lease expired. The result is unconfirmed.";
            case "MODEL_NO_PROGRESS" -> "Repeated reads produced no new result after a reminder. The Worker stopped further model calls.";
            case "MODEL_TOTAL_CALL_LIMIT" -> "The execution reached its configured model-call or turn limit.";
            case "MODEL_API_TIMEOUT" -> "The model API request timed out.";
            case "MODEL_API_CONNECTION_ERROR" -> "The Worker could not connect to the model API.";
            case "MODEL_API_AUTHENTICATION_ERROR" -> "The model API rejected authentication.";
            case "MODEL_API_PERMISSION_DENIED" -> "The model API denied this request.";
            case "MODEL_API_RATE_LIMIT" -> "The model API rate limit was reached.";
            case "MODEL_API_BAD_REQUEST" -> "The model API rejected the request parameters.";
            case "MODEL_API_NOT_FOUND" -> "The model endpoint or requested model was not found.";
            case "MODEL_API_SERVER_ERROR" -> "The model API returned a server error.";
            case "WRITABLE_WORKSPACE_REQUIRED" -> "This coding Worker requires an isolated Git workspace, but no usable revision was assigned.";
            case "GIT_HEAD_UNAVAILABLE" -> "The selected workspace has no readable current Git commit.";
            case "WORKSPACE_SNAPSHOT_PATH_TOO_LONG", "FILESYSTEM_PATH_TOO_LONG", "OUTPUT_PATH_TOO_LONG" -> "A filesystem path is too long for the current Windows operation.";
            case "WORKSPACE_SNAPSHOT_ACCESS_DENIED", "FILE_ACCESS_DENIED" -> "The Worker could not read a required file or directory.";
            case "WORKSPACE_SNAPSHOT_FILE_MISSING", "FILE_NOT_FOUND" -> "A required file or directory disappeared or could not be found.";
            case "WORKSPACE_SNAPSHOT_IO_FAILED", "FILESYSTEM_OPERATION_FAILED" -> "A filesystem operation failed while preparing or checking the workspace.";
            case "WORKSPACE_SNAPSHOT_JUNCTION_UNSAFE" -> "The workspace contains a directory junction that cannot be safely scanned.";
            case "TEXT_DECODING_FAILED" -> "A required text file could not be decoded.";
            case "OPERATION_FAILED" -> "This older failure has no specific cause recorded. It is not evidence of a missing approval.";
            default -> "The Worker reported an execution failure. Use the recorded code and attempt identity for diagnosis.";
        };
        String action=switch(code) {
            case "WORKER_LEASE_EXPIRED" -> "Check the previous attempt and confirm it has stopped before retrying.";
            case "MODEL_NO_PROGRESS", "MODEL_TOTAL_CALL_LIMIT" -> "Review the recorded work and existing results. Then decide whether to clarify the task or retry. No automatic retry was requested by this guard.";
            case "MODEL_API_TIMEOUT" -> "Check the Worker network path and provider response time before retrying.";
            case "MODEL_API_CONNECTION_ERROR" -> "Check network access from the Worker to the configured model endpoint.";
            case "MODEL_API_AUTHENTICATION_ERROR" -> "Check the configured credential and confirm the account can use this model.";
            case "MODEL_API_PERMISSION_DENIED" -> "Check model access and provider account permissions.";
            case "MODEL_API_RATE_LIMIT" -> "Wait for the provider limit to reset or select an available model profile.";
            case "MODEL_API_BAD_REQUEST" -> "Check the configured model, API protocol, and supported request settings.";
            case "MODEL_API_NOT_FOUND" -> "Check the API endpoint and configured model name.";
            case "MODEL_API_SERVER_ERROR" -> "Check the provider service status before retrying.";
            case "WRITABLE_WORKSPACE_REQUIRED" -> "Use the updated Worker: when no revision is specified, it pins the selected repository's current HEAD before coding.";
            case "GIT_HEAD_UNAVAILABLE" -> "Check that the selected directory is a Git repository with at least one commit, then retry.";
            case "WORKSPACE_SNAPSHOT_PATH_TOO_LONG", "FILESYSTEM_PATH_TOO_LONG", "OUTPUT_PATH_TOO_LONG" -> "Use the updated short output paths and Windows-compatible Worker. Preserve historical files, then retry the failed task.";
            case "WORKSPACE_SNAPSHOT_ACCESS_DENIED", "FILE_ACCESS_DENIED" -> "Check access to the assigned workspace under the Worker account before retrying.";
            case "WORKSPACE_SNAPSHOT_FILE_MISSING", "FILE_NOT_FOUND" -> "Check that the assigned workspace and required files exist before retrying.";
            default -> "Inspect the attempt evidence on the named Worker, fix the cause, then retry this task. Earlier steps are preserved.";
        };
        return new Diagnostic(code,summary,action,!"OPERATION_FAILED".equals(code));
    }
    public record RetryEvent(String actor, String reason, Instant requestedAt) {}
    public record CheckResult(String id,String status,Integer exitCode,String operationId,String outputDigest) {}

    public List<CheckResult> requiredChecks(String taskId,String attemptId,List<String> ids) {
        if(ids.isEmpty())return List.of();
        var recorded=new java.util.HashMap<String,CheckResult>();
        if(attemptId!=null) {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            for(String raw:jdbc.queryForList("SELECT delivery_json FROM execution_delivery WHERE task_id=? AND attempt_id=?",String.class,taskId,attemptId)) {
                try {
                    var delivery=mapper.readTree(raw);
                    for(var line:delivery.path("commandsRun")) {
                        var entry=mapper.readTree(line.asText());
                        if(entry==null)continue;
                        String id=entry.path("commandId").asText(), state=entry.path("checkStatus").asText();
                        if(!ids.contains(id)||!java.util.Set.of("passed","failed","stale").contains(state)||!entry.path("exitCode").isIntegralNumber())continue;
                        int exitCode=entry.path("exitCode").intValue();
                        if((state.equals("passed")&&exitCode!=0)||(state.equals("failed")&&exitCode==0))continue;
                        String operation=entry.path("operationId").asText(), digest=entry.path("outputDigest").asText();
                        if(operation.isBlank()||!digest.matches("[0-9a-f]{64}"))continue;
                        if(!Boolean.TRUE.equals(jdbc.queryForObject("""
                                SELECT EXISTS(SELECT 1 FROM execution_operation WHERE task_id=? AND attempt_id=?
                                    AND operation_id=? AND tool_id='COMMAND' AND resource=? AND state=? AND result_digest=?)
                                """,Boolean.class,taskId,attemptId,operation,id,exitCode==0?"SUCCEEDED":"FAILED",digest)))continue;
                        recorded.put(id,new CheckResult(id,switch(state) {
                            case "passed" -> "Check passed";
                            case "failed" -> "Check failed";
                            default -> "Check needs rerun";
                        },exitCode,operation,digest));
                    }
                    for(var line:delivery.path("checksNotRun"))if(ids.contains(line.asText()))
                        recorded.putIfAbsent(line.asText(),new CheckResult(line.asText(),"Check not run",null,null,null));
                } catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Unreadable evidence stays unrecorded. */ }
            }
        }
        return ids.stream().map(id->recorded.getOrDefault(id,new CheckResult(id,"Not recorded",null,null,null))).toList();
    }

    private final JdbcTemplate jdbc;

    public JdbcWorkflowTaskReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Attempt> attempts(String taskId) {
        return jdbc.query("""
                SELECT a.*, CASE WHEN a.state IN ('CLAIMED','RUNNING')
                    AND t.lease_expires_at_epoch_ms <= ? THEN 'INTERRUPTED' ELSE a.state END AS display_state
                  FROM execution_attempt a LEFT JOIN work_task t ON t.id=a.task_id AND t.lease_token=a.lease_token
                 WHERE a.task_id=?
                 ORDER BY a.attempt_no DESC, a.created_at_epoch_ms DESC, a.id
                """, (rs, n) -> new Attempt(rs.getString("id"), rs.getInt("attempt_no"),
                rs.getInt("instruction_revision"), rs.getString("worker_id"), rs.getString("display_state"),
                instant(rs, "created_at_epoch_ms"), instant(rs, "started_at_epoch_ms"),
                instant(rs, "finished_at_epoch_ms"), rs.getString("failure_category"),
                "INTERRUPTED".equals(rs.getString("display_state"))?"WORKER_LEASE_EXPIRED":code(rs.getString("termination_reason")),
                rs.getString("completion_verdict"), rs.getString("completion_summary"), rs.getString("result_revision")),
                Instant.now().toEpochMilli(),taskId);
    }

    @Transactional(readOnly = true)
    public List<RetryEvent> retries(String taskId) {
        return jdbc.query("""
                SELECT actor, reason, created_at_epoch_ms
                  FROM work_task_control_event
                 WHERE task_id=? AND action='RETRY_FAILED'
                 ORDER BY created_at_epoch_ms DESC, id DESC
                 LIMIT 50
                """, (rs, n) -> new RetryEvent(rs.getString("actor"), rs.getString("reason"),
                instant(rs, "created_at_epoch_ms")), taskId);
    }

    private static String code(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,119}") ? value : null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }
}
