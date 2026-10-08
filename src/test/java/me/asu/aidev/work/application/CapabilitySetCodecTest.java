package me.asu.aidev.work.application;

import me.asu.aidev.work.domain.CapabilitySetCodec;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitySetCodecTest {
    @Test
    void canonicalizesCapabilitiesIntoOnePortableColumn() {
        String encoded = CapabilitySetCodec.encode(Set.of("Java", "coding", "maven"));
        assertThat(encoded).isEqualTo("coding,java,maven");
        assertThat(CapabilitySetCodec.decode(encoded)).containsExactlyInAnyOrder("coding", "java", "maven");
    }
}
