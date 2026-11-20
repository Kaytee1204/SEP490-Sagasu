package com.example.base.repository;

import com.example.base.entity.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    @Query(
            value = "SELECT n FROM NewsArticle n LEFT JOIN FETCH n.author WHERE n.published = true ORDER BY n.createdAt DESC, n.id DESC",
            countQuery = "SELECT COUNT(n) FROM NewsArticle n WHERE n.published = true"
    )
    Page<NewsArticle> findPublished(Pageable pageable);

    @Query("SELECT n FROM NewsArticle n LEFT JOIN FETCH n.author WHERE n.id = :id AND n.published = true")
    Optional<NewsArticle> findPublishedById(@Param("id") Long id);

    @Query(
            value = """
                    SELECT n FROM NewsArticle n LEFT JOIN FETCH n.author
                    WHERE (:category = '' OR n.category = :category)
                      AND (:status = 'ALL'
                           OR (:status = 'PUBLISHED' AND n.published = true)
                           OR (:status = 'DRAFT' AND n.published = false))
                      AND (:pattern = '' OR LOWER(n.title) LIKE :pattern OR LOWER(n.summary) LIKE :pattern)
                    ORDER BY n.createdAt DESC, n.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(n) FROM NewsArticle n
                    WHERE (:category = '' OR n.category = :category)
                      AND (:status = 'ALL'
                           OR (:status = 'PUBLISHED' AND n.published = true)
                           OR (:status = 'DRAFT' AND n.published = false))
                      AND (:pattern = '' OR LOWER(n.title) LIKE :pattern OR LOWER(n.summary) LIKE :pattern)
                    """
    )
    Page<NewsArticle> searchForStaff(
            @Param("category") String category,
            @Param("status") String status,
            @Param("pattern") String pattern,
            Pageable pageable
    );
}
