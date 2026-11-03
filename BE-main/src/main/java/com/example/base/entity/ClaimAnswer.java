package com.example.base.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable answer and question snapshot for this dossier. */
@Entity @Table(name = "claim_answer")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false) private ClaimQuestion question;
    @Column(name = "question_code", nullable = false, length = 16) private String questionCode;
    @Column(name = "question_label", nullable = false, columnDefinition = "text") private String questionLabel;
    @Column(name = "answer_text", nullable = false, columnDefinition = "text") private String answerText;
    @Column(name = "section_title", nullable = false, length = 200) private String sectionTitle;
    @Column(name = "template_code", nullable = false, length = 64) private String templateCode;
    @Column(name = "template_version", nullable = false) private int templateVersion;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    public ClaimAnswer(ClaimQuestion question, String text, int order) {
        this.question = question;
        this.questionCode = question.getCode();
        this.questionLabel = question.getLabel();
        this.answerText = text;
        this.sectionTitle = question.getTemplate().getTitle();
        this.templateCode = question.getTemplate().getCode();
        this.templateVersion = question.getTemplate().getVersion();
        this.sortOrder = order;
    }

    void attachTo(Claim claim) { this.claim = claim; }
}
