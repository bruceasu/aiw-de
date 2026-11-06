package me.asu.aidev.execution.skills;

import java.nio.charset.StandardCharsets;
import java.util.List;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.config.ConfigurationContract;
import me.asu.aidev.execution.config.ExecutionConfigurations;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class BundledSoftwareCatalogueTest {
    // Resource loading must work without a datasource, Spring context or external process.
    private final AuthorityRepository repository = new AuthorityRepository(new JdbcTemplate());

    @Test void startupConsumersAcceptTheSameBundledCatalogue() {
        assertThatCode(() -> new SkillFiles(repository)).doesNotThrowAnyException();
        var configurations = new ExecutionConfigurations(repository, null, null, null);

        assertThat(configurations.catalogue().protocol()).isEqualTo(ConfigurationContract.PROTOCOL);
        assertThat(configurations.catalogue().methods()).filteredOn(m -> !m.method().readOnly())
                .extracting(ConfigurationContract.DefaultMethod::role)
                .containsExactlyInAnyOrder("coder", "integrator", "repairer");
        assertThat(configurations.catalogue().methods()).filteredOn(m -> !m.method().readOnly())
                .extracting(m -> m.method().resultKind()).containsOnly("DELIVERY");
    }

    @Test void changedBuiltinSkillAssetsMatchTheirPackagedFiles() throws Exception {
        var configurations = new ExecutionConfigurations(repository, null, null, null);
        for (String ref : List.of("coding-work-package-executor", "engineering-artifact-protocol")) {
            var asset = configurations.catalogue().assets().stream()
                    .filter(a -> a.kind().equals("skill") && a.ref().equals(ref)).findFirst().orElseThrow();
            try (var stream = getClass().getResourceAsStream("/worker-skills/" + ref + "/SKILL.md")) {
                assertThat(stream).as(ref).isNotNull();
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertThat(asset.content()).as(ref + " content").isEqualTo(content);
                assertThat(asset.digest()).as(ref + " digest").isEqualTo(AuthorityRepository.hash(content));
            }
        }
    }
}
