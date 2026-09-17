package de.heinzdanner.produktkompass.config;

import de.heinzdanner.produktkompass.user.AppUser;
import de.heinzdanner.produktkompass.user.AppUserRepository;
import de.heinzdanner.produktkompass.user.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (appUserRepository.findByUsername("admin").isEmpty()) {
            AppUser admin = new AppUser("admin", passwordEncoder.encode("admin123"), Role.ADMIN);
            appUserRepository.save(admin);
        }
    }
}
