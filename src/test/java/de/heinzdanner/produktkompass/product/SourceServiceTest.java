package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SourceService sourceService;

    @Test
    void createSourceSavesSource() {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        SourceRequest request = new SourceRequest("Bundesanzeiger", "https://example.de", "Regierung", verifiedAt);
        Source saved = new Source("Bundesanzeiger", "https://example.de", "Regierung", verifiedAt);

        when(sourceRepository.save(any(Source.class))).thenReturn(saved);

        Source result = sourceService.createSource(request);

        assertThat(result.getTitle()).isEqualTo("Bundesanzeiger");
        assertThat(result.getUrl()).isEqualTo("https://example.de");
    }

    @Test
    void updateSourceUpdatesExistingSource() {
        LocalDateTime oldTime = LocalDateTime.of(2025, 1, 1, 8, 0);
        LocalDateTime newTime = LocalDateTime.of(2026, 9, 1, 10, 0);
        Source existing = new Source("Alt", "https://alt.example", "Web", oldTime);
        SourceRequest request = new SourceRequest("Neu", "https://neu.example", "Studie", newTime);

        when(sourceRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(sourceRepository.save(existing)).thenReturn(existing);

        Optional<Source> result = sourceService.updateSource(4L, request);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Neu");
        assertThat(result.get().getUrl()).isEqualTo("https://neu.example");
    }
}
