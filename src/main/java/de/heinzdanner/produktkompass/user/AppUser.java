package de.heinzdanner.produktkompass.user;

import de.heinzdanner.produktkompass.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Der Benutzername darf nicht leer sein.")
    @Size(max = 255, message = "Der Benutzername darf maximal 255 Zeichen lang sein.")
    @Column(nullable = false, unique = true, length = 255)
    private String username;

    @NotBlank(message = "Das Passwort darf nicht leer sein.")
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "recovery_email", length = 254)
    private String recoveryEmail;

    @Column(name = "recovery_email_verified", nullable = false)
    private boolean recoveryEmailVerified;

    @Column(name = "verification_token_hash", length = 64, unique = true)
    private String verificationTokenHash;

    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;

    @Column(name = "verification_sent_at")
    private LocalDateTime verificationSentAt;

    @Column(name = "reset_token_hash", length = 64, unique = true)
    private String resetTokenHash;

    @Column(name = "reset_expires_at")
    private LocalDateTime resetExpiresAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    public String getRecoveryEmail() { return recoveryEmail; }
    public boolean isRecoveryEmailVerified() { return recoveryEmailVerified; }
    public String getVerificationTokenHash() { return verificationTokenHash; }
    public LocalDateTime getVerificationExpiresAt() { return verificationExpiresAt; }
    public LocalDateTime getVerificationSentAt() { return verificationSentAt; }
    public String getResetTokenHash() { return resetTokenHash; }
    public LocalDateTime getResetExpiresAt() { return resetExpiresAt; }
    public long getCredentialVersion() { return credentialVersion; }

    public void requestEmailVerification(String email, String hash, LocalDateTime expiresAt, LocalDateTime sentAt) {
        recoveryEmail = email;
        recoveryEmailVerified = false;
        verificationTokenHash = hash;
        verificationExpiresAt = expiresAt;
        verificationSentAt = sentAt;
    }

    public void confirmRecoveryEmail() {
        recoveryEmailVerified = true;
        verificationTokenHash = null;
        verificationExpiresAt = null;
    }

    public void setResetToken(String hash, LocalDateTime expiresAt) {
        resetTokenHash = hash;
        resetExpiresAt = expiresAt;
    }

    public void completePasswordReset(String encodedPassword) {
        password = encodedPassword;
        credentialVersion++;
        resetTokenHash = null;
        resetExpiresAt = null;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "app_user_favorites",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> favoriteProducts = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    /**
     * Ist das Konto aktuell (noch) gesperrt? Eine Sperre läuft automatisch
     * nach Ablauf von {@link #lockedUntil} aus.
     */
    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public Set<Product> getFavoriteProducts() {
        return favoriteProducts;
    }

    public void setFavoriteProducts(Set<Product> favoriteProducts) {
        this.favoriteProducts = favoriteProducts;
    }

    public void addFavoriteProduct(Product product) {
        this.favoriteProducts.add(product);
    }

    public void removeFavoriteProduct(Product product) {
        this.favoriteProducts.remove(product);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUser appUser)) return false;
        return Objects.equals(id, appUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
