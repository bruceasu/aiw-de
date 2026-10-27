package me.asu.aidev.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowTaskReadRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowFailureDiagnosticTest {
    @Test void pathFailureExplainsCauseAndRecovery() {
        var diagnostic=JdbcWorkflowTaskReadRepository.explain("WORKSPACE_SNAPSHOT_PATH_TOO_LONG");
        assertThat(diagnostic.specific()).isTrue();assertThat(diagnostic.summary()).contains("path is too long");
        assertThat(diagnostic.nextAction()).contains("short output paths");
    }
    @Test void olderUnknownCauseIsNotInvented() {
        assertThat(JdbcWorkflowTaskReadRepository.explain("OPERATION_FAILED").specific()).isFalse();
        assertThat(JdbcWorkflowTaskReadRepository.explain(null)).isNull();
    }
    @Test void attemptJsonContainsSafeDiagnostic() throws Exception {
        var attempt=new JdbcWorkflowTaskReadRepository.Attempt("attempt",1,1,"worker","FAILED",null,null,null,
                "TOOL_ERROR","WORKSPACE_SNAPSHOT_ACCESS_DENIED",null,null,null);
        var json=new ObjectMapper().valueToTree(attempt);
        assertThat(json.path("diagnostic").path("code").asText()).isEqualTo("WORKSPACE_SNAPSHOT_ACCESS_DENIED");
        assertThat(json.toString()).doesNotContain("leaseToken");
    }
}
