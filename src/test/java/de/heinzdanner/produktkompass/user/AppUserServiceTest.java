package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AppUserService appUserService;

    @Test
    void addFavoriteProductStoresProductOnUser() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        Product product = new Product("Bio-Hafermilch", "Klassisch");

        when(appUserRepository.findByUsername("max")).thenReturn(Optional.of(user));
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(appUserRepository.save(user)).thenReturn(user);

        AppUser result = appUserService.addFavoriteProduct("max", 5L);

        assertThat(result.getFavoriteProducts()).hasSize(1);
        assertThat(result.getFavoriteProducts().iterator().next().getName()).isEqualTo("Bio-Hafermilch");
    }

    @Test
    void getFavoriteProductsReturnsUserFavorites() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        Product product = new Product("Bio-Hafermilch", "Klassisch");
        user.addFavoriteProduct(product);

        when(appUserRepository.findByUsername("max")).thenReturn(Optional.of(user));

        List<Product> result = appUserService.getFavoriteProducts("max");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Bio-Hafermilch");
    }
}
