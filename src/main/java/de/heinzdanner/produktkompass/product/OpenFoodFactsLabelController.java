package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.product.dto.OpenFoodFactsLabelResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/off-labels")
public class OpenFoodFactsLabelController {
    private final OpenFoodFactsLabelRepository labels;

    public OpenFoodFactsLabelController(OpenFoodFactsLabelRepository labels) {
        this.labels = labels;
    }

    @GetMapping
    public LabelPage list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page muss mindestens 0 sein; size muss zwischen 1 und 50 liegen.");
        }
        var results = labels.findAll(PageRequest.of(page, size, Sort.by("canonicalTag")))
                .map(OpenFoodFactsLabelResponse::from);
        return new LabelPage(results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages());
    }

    public record LabelPage(List<OpenFoodFactsLabelResponse> content, int page, int size,
                            long totalElements, int totalPages) {
    }
}
