package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.audit.AuditLogService;
import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AppUserService implements UserDetailsService {

    /**
     * Nach so vielen aufeinanderfolgenden Fehlversuchen wird das Konto
     * vorübergehend gesperrt (Schutz gegen Brute-Force-Angriffe).
     */
    static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    /**
     * Dauer der automatischen Sperre nach zu vielen Fehlversuchen.
     */
    static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AppUserService(AppUserRepository appUserRepository,
                          ProductRepository productRepository,
                          PasswordEncoder passwordEncoder,
                          AuditLogService auditLogService) {
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username));

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPassword())
                .roles(appUser.getRole().name())
                .disabled(!appUser.isEnabled())
                .accountLocked(appUser.isCurrentlyLocked())
                .build();
    }

    /**
     * Wird nach einem fehlgeschlagenen Login-Versuch aufgerufen. Zählt die
     * Fehlversuche des Benutzers hoch und sperrt das Konto vorübergehend,
     * sobald {@link #MAX_FAILED_LOGIN_ATTEMPTS} erreicht ist.
     */
    @Transactional
    public void registerFailedLogin(String username) {
        appUserRepository.findByUsername(normalizeUsername(username)).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plus(LOCKOUT_DURATION));
            }
            appUserRepository.save(user);
        });
    }

    /**
     * Setzt den Fehlversuchszähler und eine eventuelle Sperre nach einem
     * erfolgreichen Login zurück.
     */
    @Transactional
    public void registerSuccessfulLogin(String username) {
        appUserRepository.findByUsername(normalizeUsername(username)).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                appUserRepository.save(user);
            }
        });
    }

    public List<AppUser> getAllUsers() {
        return appUserRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional
    public AppUser updateRole(Long userId, Role newRole, String actingUsername) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer wurde nicht gefunden."));

        if (user.getUsername().equals(actingUsername) && user.getRole() != newRole) {
            throw new IllegalArgumentException("Die eigene Rolle kann nicht geändert werden.");
        }
        if (user.getRole() == Role.ADMIN
                && newRole != Role.ADMIN
                && appUserRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("Der letzte Administrator kann nicht herabgestuft werden.");
        }
        if (user.getRole() == newRole) {
            return user;
        }

        Role previousRole = user.getRole();
        user.setRole(newRole);
        AppUser savedUser = appUserRepository.save(user);
        auditLogService.logAction("AppUser", savedUser.getId(), "ROLE_UPDATE",
                "Benutzer " + savedUser.getUsername() + ": " + previousRole + " -> " + newRole
                        + " (geändert von " + actingUsername + ")");
        return savedUser;
    }

    @Transactional
    public AppUser unlockUser(Long userId, String actingUsername) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Benutzer wurde nicht gefunden."));

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        AppUser savedUser = appUserRepository.save(user);
        auditLogService.logAction("AppUser", savedUser.getId(), "UNLOCK",
                "Benutzer " + savedUser.getUsername() + " entsperrt von " + actingUsername);
        return savedUser;
    }

    public Optional<AppUser> findByUsername(String username) {
        return appUserRepository.findByUsername(username);
    }

    public List<Product> getFavoriteProducts(String username) {
        AppUser user = appUserRepository.findByUsernameWithFavorites(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username));

        return user.getFavoriteProducts().stream().toList();
    }

    public Set<Long> getFavoriteProductIds(String username) {
        return appUserRepository.findByUsernameWithFavorites(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username))
                .getFavoriteProducts()
                .stream()
                .map(Product::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Transactional
    public AppUser addFavoriteProduct(String username, Long productId) {
        AppUser user = appUserRepository.findByUsernameWithFavorites(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden: " + productId));

        user.addFavoriteProduct(product);
        return appUserRepository.save(user);
    }

    @Transactional
    public boolean removeFavoriteProduct(String username, Long productId) {
        AppUser user = appUserRepository.findByUsernameWithFavorites(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden: " + productId));

        if (!user.getFavoriteProducts().contains(product)) {
            return false;
        }

        user.removeFavoriteProduct(product);
        appUserRepository.save(user);
        return true;
    }

    public boolean isFavoriteProduct(String username, Long productId) {
        return appUserRepository.findByUsernameWithFavorites(username)
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username))
                .getFavoriteProducts()
                .stream()
                .anyMatch(product -> product.getId() != null && product.getId().equals(productId));
    }

    @Transactional
    public AppUser registerUser(String username, String rawPassword, Role role) {
        String normalizedUsername = normalizeUsername(username);
        if (appUserRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Dieser Benutzername ist bereits vergeben. Bitte wähle einen anderen.");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        AppUser appUser = new AppUser(normalizedUsername, encodedPassword, role);
        return appUserRepository.save(appUser);
    }

    /**
     * Entfernt führende/nachfolgende Leerzeichen sowie doppelte Leerzeichen
     * innerhalb des Benutzernamens, damit z. B. "max " und "max" nicht als
     * unterschiedliche Konten behandelt werden.
     */
    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        return username.trim().replaceAll("\\s+", " ");
    }
}
