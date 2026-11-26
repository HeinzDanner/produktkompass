package de.heinzdanner.produktkompass.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryConfigurationTest {
    @Test
    void composeMailpitIsLocalAndExistingServicesHaveValidVolumes() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("docker-compose.yml"))) {
            Map<?, ?> compose = new Yaml().load(reader);
            var services = (Map<?, ?>) compose.get("services");
            var mailpit = (Map<?, ?>) services.get("mailpit");
            assertThat(mailpit.get("profiles")).isEqualTo(java.util.List.of("mail"));
            assertThat(mailpit.get("ports")).isEqualTo(java.util.List.of("127.0.0.1:1025:1025", "127.0.0.1:8025:8025"));
            var postgres = (Map<?, ?>) services.get("postgres");
            assertThat(postgres.get("volumes")).isEqualTo(java.util.List.of("postgres_data:/var/lib/postgresql/data"));
            assertThat(((Map<?, ?>) compose.get("volumes")).keySet().stream().map(Object::toString).toList())
                    .contains("postgres_data", "mealie_data");
        }
    }
}
