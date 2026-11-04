package com.example.base.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "claim_question")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false) private ClaimQuestionTemplate template;
    @Column(name = "question_code", nullable = false, length = 16) private String code;
    @Column(nullable = false, columnDefinition = "text") private String label;
    @Column(name = "input_type", nullable = false, length = 24) private String inputType;
    @Column(name = "is_required", nullable = false) private boolean required;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode options;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode visibility;
    @Column(name = "allow_details", nullable = false) private boolean allowDetails;
    @Column(name = "choice_prompt", columnDefinition = "text") private String choicePrompt;
}
