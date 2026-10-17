package me.asu.aidev.workflow.application;

import java.util.List;
import me.asu.aidev.ai.application.AiProfileCatalog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecommendationPolicyTest {
    @Test void explicitUnknownPlannerIsRejectedInsteadOfFallingBack() {
        var p = new AiProfileCatalog.NamedProfile("approved",
                new AiProfileCatalog.Profile("openai", "codex-cli", "model-a", "high", false, 1));
        assertThatThrownBy(() -> RecommendationProfiles.filter(List.of(p), "missing", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("planner");
    }
}
