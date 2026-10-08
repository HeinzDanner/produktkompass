package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.product.dto.ProductResponse;
import de.heinzdanner.produktkompass.user.dto.UserRegistrationRequest;
import de.heinzdanner.produktkompass.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class AppUserController {

    private final AppUserService appUserService;

    public AppUserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        AppUser savedUser = appUserService.registerUser(request.username(), request.password(), Role.USER);
        return ResponseEntity.ok(UserResponse.fromEntity(savedUser));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        AppUser user = appUserService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authentifizierter Benutzer wurde nicht gefunden."));

        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping("/me/favorites")
    public ResponseEntity<List<ProductResponse>> getFavoriteProducts() {
        String username = currentUsername();
        return ResponseEntity.ok(appUserService.getFavoriteProductResponses(username));
    }

    @PostMapping("/me/favorites/{productId}")
    public ResponseEntity<ProductResponse> addFavoriteProduct(@PathVariable Long productId) {
        String username = currentUsername();
        return ResponseEntity.ok(appUserService.addFavoriteProductResponse(username, productId));
    }

    @DeleteMapping("/me/favorites/{productId}")
    public ResponseEntity<Void> removeFavoriteProduct(@PathVariable Long productId) {
        String username = currentUsername();
        return appUserService.removeFavoriteProduct(username, productId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("Kein authentifizierter Benutzer vorhanden.");
        }

        return authentication.getName();
    }
}
