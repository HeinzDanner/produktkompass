package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.audit.AuditLogService;
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

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AppUserService appUserService;

    @Test
    void addFavoriteProductStoresProductOnUser() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        Product product = new Product("Bio-Hafermilch", "Klassisch");

        when(appUserRepository.findByUsernameWithFavorites("max")).thenReturn(Optional.of(user));
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

        when(appUserRepository.findByUsernameWithFavorites("max")).thenReturn(Optional.of(user));

        List<Product> result = appUserService.getFavoriteProducts("max");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("Bio-Hafermilch");
    }

    @Test
    void registerUserTrimsAndCollapsesWhitespaceInUsername() {
        when(appUserRepository.findByUsername("max mustermann")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Passwort1")).thenReturn("encoded");
        when(appUserRepository.save(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppUser result = appUserService.registerUser("  max   mustermann  ", "Passwort1", Role.USER);

        assertThat(result.getUsername()).isEqualTo("max mustermann");
    }

    @Test
    void registerUserRejectsDuplicateUsername() {
        when(appUserRepository.findByUsername("max")).thenReturn(Optional.of(new AppUser("max", "pw", Role.USER)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> appUserService.registerUser("max", "Passwort1", Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bereits vergeben");
    }

    @Test
    void registerFailedLoginLocksAccountAfterMaxAttempts() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        when(appUserRepository.findByUsernameForUpdate("max")).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        for (int i = 0; i < AppUserService.MAX_FAILED_LOGIN_ATTEMPTS - 1; i++) {
            appUserService.registerFailedLogin("max");
        }
        assertThat(user.getFailedLoginAttempts()).isEqualTo(AppUserService.MAX_FAILED_LOGIN_ATTEMPTS - 1);
        assertThat(user.isCurrentlyLocked()).isFalse();

        appUserService.registerFailedLogin("max");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(AppUserService.MAX_FAILED_LOGIN_ATTEMPTS);
        assertThat(user.isCurrentlyLocked()).isTrue();
        assertThat(user.getLockedUntil()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    void registerSuccessfulLoginResetsFailedAttemptsAndLock() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        user.setFailedLoginAttempts(4);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(appUserRepository.findByUsernameForUpdate("max")).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        appUserService.registerSuccessfulLogin("max");

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isCurrentlyLocked()).isFalse();
    }

    @Test
    void attemptsDuringLockDoNotExtendLockOrIncreaseCount() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        var expiry = java.time.LocalDateTime.now().plusMinutes(10);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(expiry);
        when(appUserRepository.findByUsernameForUpdate("max")).thenReturn(Optional.of(user));

        appUserService.registerFailedLogin("max");

        assertThat(user.getLockedUntil()).isEqualTo(expiry);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        org.mockito.Mockito.verify(appUserRepository, org.mockito.Mockito.never()).save(user);
    }

    @Test
    void firstFailureAfterExpiredLockStartsFreshCounter() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));
        when(appUserRepository.findByUsernameForUpdate("max")).thenReturn(Optional.of(user));

        appUserService.registerFailedLogin("max");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        org.mockito.Mockito.verify(appUserRepository).save(user);
    }

    @Test
    void expiredLockDoesNotCountAsCurrentlyLocked() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        user.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));

        assertThat(user.isCurrentlyLocked()).isFalse();
    }

    @Test
    void updateRoleChangesAnotherUsersRole() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        AppUser result = appUserService.updateRole(5L, Role.EDITOR, "admin");

        assertThat(result.getRole()).isEqualTo(Role.EDITOR);
    }

    @Test
    void updateRoleRejectsChangingOwnRole() {
        AppUser admin = new AppUser("admin", "pw", Role.ADMIN);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> appUserService.updateRole(1L, Role.USER, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eigene Rolle");
    }

    @Test
    void updateRoleRejectsDemotingLastAdministrator() {
        AppUser admin = new AppUser("other-admin", "pw", Role.ADMIN);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(appUserRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> appUserService.updateRole(2L, Role.EDITOR, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letzte Administrator");
    }

    @Test
    void unlockUserClearsLockAndFailedAttempts() {
        AppUser user = new AppUser("max", "pw", Role.USER);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        when(appUserRepository.findById(5L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        AppUser result = appUserService.unlockUser(5L, "admin");

        assertThat(result.getFailedLoginAttempts()).isZero();
        assertThat(result.getLockedUntil()).isNull();
    }
}
