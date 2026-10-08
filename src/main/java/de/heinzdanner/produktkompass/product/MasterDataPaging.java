package de.heinzdanner.produktkompass.product;

import de.heinzdanner.produktkompass.product.dto.LookupOption;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class MasterDataPaging {
    private MasterDataPaging() {
    }

    static PageRequest request(int page, int size, String property) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page muss mindestens 0 sein; size muss zwischen 1 und 100 liegen.");
        }
        return PageRequest.of(page, size,
                Sort.by(property).ascending().and(Sort.by("id").ascending()));
    }

    static String pattern(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    static <T> List<LookupOption> options(Collection<Long> selectedIds, List<T> initial,
                                         Function<Long, Optional<T>> lookup,
                                         Function<T, Long> id, Function<T, String> name) {
        var options = new LinkedHashMap<Long, LookupOption>();
        initial.forEach(item -> options.put(id.apply(item), new LookupOption(id.apply(item), name.apply(item))));
        if (selectedIds != null) {
            selectedIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(selected -> {
                if (!options.containsKey(selected)) {
                    options.put(selected, lookup.apply(selected)
                            .map(item -> new LookupOption(selected, name.apply(item)))
                            .orElseGet(() -> new LookupOption(selected, "Nicht verfügbar (#" + selected + ")")));
                }
            });
        }
        return List.copyOf(options.values());
    }
}
