package me.asu.aidev.autonomous.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImplementationPlanParserTest {
    private final ImplementationPlanParser parser = new ImplementationPlanParser();

    @Test
    void parsesMultipleIndependentWorkItemsFromHumanReadableSummary() {
        String summary = """
                Implementation plan overview.

                AIW_IMPLEMENTATION_WORK_ITEMS_V1
                AIW_WORK_ITEM_BEGIN api
                AIW_CAPABILITIES java,maven
                Add the endpoint and application service wiring.
                Keep the public contract minimal.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN tests
                AIW_CAPABILITIES java,maven
                Add focused integration and regression tests.
                AIW_WORK_ITEM_END
                AIW_IMPLEMENTATION_WORK_ITEMS_END
                """;

        var manifest = parser.parse(summary).orElseThrow();
        assertThat(manifest.items()).hasSize(2);
        assertThat(manifest.items().get(0).key()).isEqualTo("api");
        assertThat(manifest.items().get(0).capabilities()).containsExactlyInAnyOrder("java", "maven");
        assertThat(manifest.items().get(0).instruction()).contains("endpoint").contains("public contract");
        assertThat(manifest.items().get(1).key()).isEqualTo("tests");
    }

    @Test
    void rejectsDuplicateWorkItemKeys() {
        String summary = """
                AIW_IMPLEMENTATION_WORK_ITEMS_V1
                AIW_WORK_ITEM_BEGIN api
                First.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN api
                Second.
                AIW_WORK_ITEM_END
                AIW_IMPLEMENTATION_WORK_ITEMS_END
                """;
        assertThatThrownBy(() -> parser.parse(summary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate work item key");
    }
}
