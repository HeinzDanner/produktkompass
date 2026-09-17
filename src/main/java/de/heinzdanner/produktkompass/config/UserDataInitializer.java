package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.Role;
import de.heinzdanner.produktkompass.product.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(AppUserRepository appUserRepository, ProductRepository productRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUser("admin", "admin123", Role.ADMIN);
        ensureUser("max", "pw", Role.USER);
    }

    private void ensureUser(String username, String rawPassword, Role role) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseGet(() -> new AppUser(username, passwordEncoder.encode(rawPassword), role));

        if (user.getId() == null && "max".equals(username)) {
            productRepository.findAll().stream().findFirst().ifPresent(user::addFavoriteProduct);
        }

        appUserRepository.save(user);
    }
}
