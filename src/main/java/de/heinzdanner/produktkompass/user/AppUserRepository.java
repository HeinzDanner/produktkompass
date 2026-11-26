package de.heinzdanner.produktkompass.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.username = :username")
    Optional<AppUser> findByUsernameForUpdate(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> findByIdForUpdate(Long id);

    @Query("SELECT u.id FROM AppUser u WHERE u.verificationTokenHash = :hash")
    Optional<Long> findIdByVerificationTokenHash(String hash);

    @Query("SELECT u.id FROM AppUser u WHERE u.resetTokenHash = :hash")
    Optional<Long> findIdByResetTokenHash(String hash);

    @Query("SELECT u.credentialVersion FROM AppUser u WHERE u.username = :username AND u.enabled = true")
    Optional<Long> findActiveCredentialVersion(String username);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByRole(Role role);

    @Query("SELECT p.id FROM AppUser u JOIN u.favoriteProducts p WHERE u.username = :username AND p.id IN :productIds")
    Set<Long> findFavoriteIdsForProducts(String username, List<Long> productIds);

    @Query("SELECT DISTINCT u FROM AppUser u LEFT JOIN FETCH u.favoriteProducts fp LEFT JOIN FETCH fp.brand LEFT JOIN FETCH fp.company LEFT JOIN FETCH fp.categories LEFT JOIN FETCH fp.sources WHERE u.username = :username")
    Optional<AppUser> findByUsernameWithFavorites(String username);
}
