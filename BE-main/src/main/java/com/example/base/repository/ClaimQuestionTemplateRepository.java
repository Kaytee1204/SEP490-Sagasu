package com.example.base.repository;

import com.example.base.entity.ClaimQuestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClaimQuestionTemplateRepository extends JpaRepository<ClaimQuestionTemplate, Long> {
    Optional<ClaimQuestionTemplate> findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(Long categoryId);
    Optional<ClaimQuestionTemplate> findFirstByCodeAndActiveTrueOrderByVersionDesc(String code);
}
