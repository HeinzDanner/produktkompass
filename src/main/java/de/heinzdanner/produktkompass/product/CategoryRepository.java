package de.heinzdanner.produktkompass.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("select new de.heinzdanner.produktkompass.product.dto.LookupOption(c.id, c.name) from Category c order by c.name, c.id")
    java.util.List<de.heinzdanner.produktkompass.product.dto.LookupOption> findPublicOptions();

    @Query("select c from Category c where lower(c.name) like :pattern escape '!'")
    Page<Category> search(@Param("pattern") String pattern, Pageable pageable);
}
