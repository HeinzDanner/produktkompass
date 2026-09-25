package de.heinzdanner.produktkompass.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    List<AppUser> findAllByOrderByUsernameAsc();

    long countByRole(Role role);

    @Query("SELECT DISTINCT u FROM AppUser u LEFT JOIN FETCH u.favoriteProducts fp LEFT JOIN FETCH fp.brand LEFT JOIN FETCH fp.company LEFT JOIN FETCH fp.categories LEFT JOIN FETCH fp.sources WHERE u.username = :username")
    Optional<AppUser> findByUsernameWithFavorites(String username);
}
