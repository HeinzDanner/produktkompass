package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.product.dto.SourceRequest;
import de.heinzdanner.produktkompass.product.dto.SourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    public ResponseEntity<List<SourceResponse>> getAllSources() {
        List<SourceResponse> sources = sourceService.getAllSources()
                .stream()
                .map(SourceResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(sources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> getSourceById(@PathVariable Long id) {
        return sourceService.getSourceById(id)
                .map(SourceResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SourceResponse> createSource(@Valid @RequestBody SourceRequest request) {
        SourceResponse response = SourceResponse.fromEntity(sourceService.createSource(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceResponse> updateSource(@PathVariable Long id, @Valid @RequestBody SourceRequest request) {
        return sourceService.updateSource(id, request)
                .map(SourceResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long id) {
        return sourceService.deleteSource(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
