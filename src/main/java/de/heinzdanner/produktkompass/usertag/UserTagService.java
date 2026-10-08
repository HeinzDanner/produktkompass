package de.heinzdanner.produktkompass.usertag;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verwaltet persönliche Tags eines Benutzers. Jede Methode, die einen
 * vorhandenen Tag liest/ändert, prüft zuerst über {@link #getOwnedTagOrThrow},
 * dass der Tag tatsächlich dem anfragenden Benutzer gehört ("ownership check").
 * So kann ein Benutzer niemals Tags eines anderen Benutzers sehen oder verändern.
 */
@Service
@Transactional(readOnly = true)
public class UserTagService {

    private final UserTagRepository userTagRepository;
    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    /**
     * Maximale Anzahl eigener Tags pro Benutzer. Konfigurierbar über
     * {@code produktkompass.user-tags.max-per-user} (Standard: 5), damit sich
     * dieser Wert später ohne Codeänderung anpassen lässt.
     */
    @Value("${produktkompass.user-tags.max-per-user:5}")
    private int maxTagsPerUser;

    public UserTagService(UserTagRepository userTagRepository,
                          AppUserRepository appUserRepository,
                          ProductRepository productRepository,
                          BrandRepository brandRepository) {
        this.userTagRepository = userTagRepository;
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    public int getMaxTagsPerUser() {
        return maxTagsPerUser;
    }

    public List<UserTag> getTagsForUser(String username) {
        return userTagRepository.findAllWithAssignmentsByOwnerUsername(username);
    }

    public List<UserTag> getTagOptionsForUser(String username) {
        return userTagRepository.findAllByOwnerUsernameOrderByNameAsc(username);
    }

    @Transactional
    public UserTag createTag(String username, String rawName) {
        String name = normalizeName(rawName);

        if (userTagRepository.countByOwnerUsername(username) >= maxTagsPerUser) {
            throw new IllegalStateException(
                    "Du hast bereits die maximale Anzahl von " + maxTagsPerUser + " eigenen Tags erreicht.");
        }
        if (userTagRepository.existsByOwnerUsernameAndNameIgnoreCase(username, name)) {
            throw new IllegalArgumentException("Du hast bereits einen Tag mit diesem Namen.");
        }

        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Benutzer nicht gefunden: " + username));

        return userTagRepository.save(new UserTag(name, owner));
    }

    @Transactional
    public void deleteTag(String username, Long tagId) {
        UserTag tag = getOwnedTagOrThrow(username, tagId);
        userTagRepository.delete(tag);
    }

    /**
     * Gleicht die Tag-Zuordnung eines Produkts mit der übergebenen Auswahl ab:
     * Tags aus {@code selectedTagIds} werden zugewiesen, alle anderen eigenen
     * Tags des Benutzers werden vom Produkt entfernt, falls vorhanden.
     */
    @Transactional
    public void syncProductTags(String username, Long productId, Set<Long> selectedTagIds) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden: " + productId));

        List<UserTag> ownTags = userTagRepository.findAllWithAssignmentsByOwnerUsername(username);
        for (UserTag tag : ownTags) {
            boolean shouldBeAssigned = selectedTagIds.contains(tag.getId());
            boolean isAssigned = tag.getProducts().contains(product);
            if (shouldBeAssigned && !isAssigned) {
                tag.getProducts().add(product);
            } else if (!shouldBeAssigned && isAssigned) {
                tag.getProducts().remove(product);
            }
        }
        userTagRepository.saveAll(ownTags);
    }

    /**
     * Gleicht die Tag-Zuordnung einer Marke mit der übergebenen Auswahl ab
     * (analog zu {@link #syncProductTags}).
     */
    @Transactional
    public void syncBrandTags(String username, Long brandId, Set<Long> selectedTagIds) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("Marke nicht gefunden: " + brandId));

        List<UserTag> ownTags = userTagRepository.findAllWithAssignmentsByOwnerUsername(username);
        for (UserTag tag : ownTags) {
            boolean shouldBeAssigned = selectedTagIds.contains(tag.getId());
            boolean isAssigned = tag.getBrands().contains(brand);
            if (shouldBeAssigned && !isAssigned) {
                tag.getBrands().add(brand);
            } else if (!shouldBeAssigned && isAssigned) {
                tag.getBrands().remove(brand);
            }
        }
        userTagRepository.saveAll(ownTags);
    }

    /**
     * Liefert die IDs aller Produkte, die für die Suche zu diesem Tag zählen:
     * direkt mit dem Tag versehene Produkte plus alle Produkte von Marken,
     * die mit dem Tag versehen sind.
     */
    public Set<Long> resolveProductIdsForTag(String username, Long tagId) {
        UserTag tag = getOwnedTagOrThrow(username, tagId);

        Set<Long> productIds = new HashSet<>();
        tag.getProducts().forEach(product -> productIds.add(product.getId()));
        for (Brand brand : tag.getBrands()) {
            productRepository.findByBrandId(brand.getId())
                    .forEach(product -> productIds.add(product.getId()));
        }
        return productIds;
    }

    public void requireOwnedTag(String username, Long tagId) {
        if (username == null || !userTagRepository.existsByIdAndOwnerUsername(tagId, username)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Tag wurde nicht gefunden.");
        }
    }

    private UserTag getOwnedTagOrThrow(String username, Long tagId) {
        return userTagRepository.findByIdWithAssignmentsAndOwnerUsername(tagId, username)
                .orElseThrow(() -> new IllegalArgumentException("Tag wurde nicht gefunden oder gehört dir nicht."));
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Der Tag-Name darf nicht leer sein.");
        }
        String trimmed = name.trim().replaceAll("\\s+", " ");
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Der Tag-Name darf nicht leer sein.");
        }
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException("Der Tag-Name darf maximal 50 Zeichen lang sein.");
        }
        return trimmed;
    }
}
