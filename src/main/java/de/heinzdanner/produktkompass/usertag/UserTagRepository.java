package de.heinzdanner.produktkompass.usertag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTagRepository extends JpaRepository<UserTag, Long> {

    long countByOwnerUsername(String username);

    boolean existsByOwnerUsernameAndNameIgnoreCase(String username, String name);

    @Query("SELECT DISTINCT t FROM UserTag t LEFT JOIN FETCH t.products LEFT JOIN FETCH t.brands " +
            "WHERE t.owner.username = :username ORDER BY t.name ASC")
    List<UserTag> findAllWithAssignmentsByOwnerUsername(@Param("username") String username);

    @Query("SELECT t FROM UserTag t LEFT JOIN FETCH t.products LEFT JOIN FETCH t.brands " +
            "WHERE t.id = :id AND t.owner.username = :username")
    Optional<UserTag> findByIdWithAssignmentsAndOwnerUsername(@Param("id") Long id, @Param("username") String username);
}
