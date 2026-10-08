package de.heinzdanner.produktkompass.usertag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserTagRepository extends JpaRepository<UserTag, Long> {

    long countByOwnerUsername(String username);

    boolean existsByOwnerUsernameAndNameIgnoreCase(String username, String name);

    boolean existsByIdAndOwnerUsername(Long id, String username);

    List<UserTag> findAllByOwnerUsernameOrderByNameAsc(String username);

    @Query("SELECT t.id FROM UserTag t JOIN t.brands b WHERE t.owner.username = :username AND b.id = :brandId")
    Set<Long> findAssignedIdsForBrand(String username, Long brandId);

    @Query("SELECT t.id FROM UserTag t JOIN t.products p WHERE t.owner.username = :username AND p.id = :productId")
    Set<Long> findAssignedIdsForProduct(String username, Long productId);

    @Query("SELECT DISTINCT t FROM UserTag t LEFT JOIN FETCH t.products LEFT JOIN FETCH t.brands " +
            "WHERE t.owner.username = :username ORDER BY t.name ASC")
    List<UserTag> findAllWithAssignmentsByOwnerUsername(@Param("username") String username);

    @Query("SELECT t FROM UserTag t LEFT JOIN FETCH t.products LEFT JOIN FETCH t.brands " +
            "WHERE t.id = :id AND t.owner.username = :username")
    Optional<UserTag> findByIdWithAssignmentsAndOwnerUsername(@Param("id") Long id, @Param("username") String username);
}
