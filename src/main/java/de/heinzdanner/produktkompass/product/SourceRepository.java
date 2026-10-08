package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {
    @Query("select s from Source s where lower(s.title) like :pattern escape '!'")
    Page<Source> search(@Param("pattern") String pattern, Pageable pageable);
}
