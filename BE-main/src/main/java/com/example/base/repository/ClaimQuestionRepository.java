package com.example.base.repository;

import com.example.base.entity.ClaimQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimQuestionRepository extends JpaRepository<ClaimQuestion, Long> {
    @EntityGraph(attributePaths = "template")
    List<ClaimQuestion> findByTemplate_IdOrderBySortOrderAscIdAsc(long templateId);
}
