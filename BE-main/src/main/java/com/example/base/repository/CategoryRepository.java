package com.example.base.repository;

import com.example.base.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
        SELECT c FROM Category c
        WHERE (:active IS NULL OR c.active = :active)
          AND LOCATE(LOWER(:keyword), LOWER(c.name)) > 0
        """)
    Page<Category> search(@Param("keyword") String keyword, @Param("active") Boolean active, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndCategoryIdNot(String name, Long categoryId);
}
