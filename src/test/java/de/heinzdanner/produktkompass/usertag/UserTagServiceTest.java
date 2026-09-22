package de.heinzdanner.produktkompass.usertag;

import de.heinzdanner.produktkompass.product.Brand;
import de.heinzdanner.produktkompass.product.BrandRepository;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTagServiceTest {

    @Mock
    private UserTagRepository userTagRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private UserTagService userTagService;

    @Test
    void createTagRejectsWhenLimitReached() {
        ReflectionTestUtils.setField(userTagService, "maxTagsPerUser", 5);
        when(userTagRepository.countByOwnerUsername("max")).thenReturn(5L);

        assertThatThrownBy(() -> userTagService.createTag("max", "Neuer Tag"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximale Anzahl");
    }

    @Test
    void createTagRejectsDuplicateNameCaseInsensitive() {
        ReflectionTestUtils.setField(userTagService, "maxTagsPerUser", 5);
        when(userTagRepository.countByOwnerUsername("max")).thenReturn(1L);
        when(userTagRepository.existsByOwnerUsernameAndNameIgnoreCase("max", "Bio")).thenReturn(true);

        assertThatThrownBy(() -> userTagService.createTag("max", "Bio"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bereits einen Tag");
    }

    @Test
    void createTagTrimsAndSavesNewTag() {
        ReflectionTestUtils.setField(userTagService, "maxTagsPerUser", 5);
        AppUser owner = new AppUser("max", "pw", Role.USER);
        when(userTagRepository.countByOwnerUsername("max")).thenReturn(0L);
        when(userTagRepository.existsByOwnerUsernameAndNameIgnoreCase("max", "Bio")).thenReturn(false);
        when(appUserRepository.findByUsername("max")).thenReturn(Optional.of(owner));
        when(userTagRepository.save(any(UserTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTag result = userTagService.createTag("max", "  Bio  ");

        assertThat(result.getName()).isEqualTo("Bio");
        assertThat(result.getOwner()).isEqualTo(owner);
    }

    @Test
    void deleteTagRejectsWhenTagBelongsToAnotherUser() {
        when(userTagRepository.findByIdWithAssignmentsAndOwnerUsername(1L, "eve")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userTagService.deleteTag("eve", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gehört dir nicht");
    }

    @Test
    void resolveProductIdsForTagCombinesDirectAndBrandProducts() {
        AppUser owner = new AppUser("max", "pw", Role.USER);
        UserTag tag = new UserTag("Bio", owner);

        Product directProduct = new Product("Hafermilch", "desc");
        ReflectionTestUtils.setField(directProduct, "id", 1L);
        tag.getProducts().add(directProduct);

        Brand brand = new Brand("Alnatura", "desc");
        ReflectionTestUtils.setField(brand, "id", 10L);
        tag.getBrands().add(brand);

        Product brandProduct = new Product("Bio-Joghurt", "desc");
        ReflectionTestUtils.setField(brandProduct, "id", 2L);

        when(userTagRepository.findByIdWithAssignmentsAndOwnerUsername(5L, "max")).thenReturn(Optional.of(tag));
        when(productRepository.findByBrandId(10L)).thenReturn(List.of(brandProduct));

        Set<Long> result = userTagService.resolveProductIdsForTag("max", 5L);

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }
}
