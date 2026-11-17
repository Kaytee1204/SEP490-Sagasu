package com.example.base.dto.claim;
import java.time.OffsetDateTime;
import java.util.List;
/** Owner-only, paginated evidence for P2P review; no ownership scores. */
public record ClaimComparisonDto(List<Entry> content, int page, int size, long totalElements, int totalPages) {
    public record Entry(Long id, Long postId, String claimantName, String status, OffsetDateTime createdAt,
            String description, List<Answer> answers, List<ClaimDetailDto.EvidenceDto> evidence, Completion completion) {}
    /** Questionnaire completion only; images and ownership verification are separate. */
    public record Completion(int answered, int total) {}
    public record Answer(String questionCode, String questionLabel, String answerText,
            String templateCode, int templateVersion, boolean required, int sortOrder) {}
}
