package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.product.Product;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AppUserService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository appUserRepository,
                          ProductRepository productRepository,
                          PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("Benutzer nicht gefunden: " + username));

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPassword())
                .roles(appUser.getRole().name())
                .disabled(!appUser.isEnabled())
                .build();
    }

    public List<AppUser> getAllUsers() {
        return appUserRepository.findAll();
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
