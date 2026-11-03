package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "claim_question_template")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimQuestionTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id") private Long id;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String title;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id") private Category category;
    @Column(nullable = false) private int version;
    @Column(name = "is_active", nullable = false) private boolean active;
}
