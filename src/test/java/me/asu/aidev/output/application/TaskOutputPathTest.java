package me.asu.aidev.output.application;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskOutputPathTest {
    @Test void archivePathUsesOnlyAFixedLengthAttemptDigest() {
        var root=Path.of("workspace");
        var first=TaskOutputService.archiveDirectory(root,"attempt-1");
        assertThat(root.relativize(first).getNameCount()).isEqualTo(3);
        assertThat(first.getFileName().toString()).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(TaskOutputService.archiveDirectory(root,"attempt-1"))
                .isNotEqualTo(TaskOutputService.archiveDirectory(root,"attempt-2"));
        assertThat(TaskOutputService.archiveDirectory(root,"x".repeat(1000)).toString().length()).isEqualTo(first.toString().length());
    }
}
