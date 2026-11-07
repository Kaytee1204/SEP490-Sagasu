package com.example.base.dto.office;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import com.example.base.dto.claim.ClaimDetailDto;

public final class OfficeDtos {
    private OfficeDtos() {}
    public record PageData<T>(List<T> content, int page, long totalElements, int totalPages) {}
    public record Moderation(@NotBlank @Pattern(regexp="VISIBLE|HIDDEN") String status,
        @NotBlank @Size(max=2000) String reason, @NotNull OffsetDateTime expectedUpdatedAt) {}
    public record LocationInput(@NotBlank @Size(max=40) String code, @NotBlank @Size(max=150) String name,
        Long parentId, boolean active) {}
    public record LocationView(Long id, String code, String name, Long parentId, boolean active) {}
    public record ReceiptView(Long id, String code, Long postId, String title, String status, String condition,
        String location, Long locationId, String receivedBy, OffsetDateTime receivedAt, String finderName, String finderContact) {}
    public record PostView(Long id, String title, String description, String postType, String status,
        String moderationStatus, String custodyMode, String category, String author, String authorEmail,
        String location, OffsetDateTime createdAt, OffsetDateTime updatedAt, String moderationNote,
        String moderatedBy, OffsetDateTime moderatedAt, List<String> images, ReceiptView receipt) {}
    public record ClaimantOption(Long id, String name, String email) {}
    public record Submission(@NotNull @Positive Long postId, @Positive Long claimantId,
        @Size(max=150) String claimantName, @Email @Size(max=255) String claimantEmail,
        @Size(max=40) String claimantPhone, @NotBlank @Size(max=5000) String description,
        @Size(max=5000) String evidenceNote) {}
    public record Decision(@NotBlank @Pattern(regexp="APPROVED|REJECTED|NEEDS_INFO") String decision,
        @NotBlank @Size(max=2000) String reason, @NotNull @PositiveOrZero Long expectedVersion,
        OffsetDateTime scheduledAt, @Size(max=1000) String location,
        @Email @Size(max=255) String email, @Size(max=2000) String appointmentNote) {}
    public record Supplement(@NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=2000) String information) {}
    public record AppointmentView(Long id, OffsetDateTime scheduledAt, String location, String note, String status,
        String responseStatus, OffsetDateTime responseDeadline, String cancellationReason) {}
    public record AnswerView(Long id, String question, String answer, String section) {}
    public record ClaimView(Long id, Long postId, String postTitle, String status, String channel,
        Long claimantId, String claimantName, String claimantEmail, String claimantPhone, String createdBy,
        String description, String evidenceNote, String privateIdentifiers, long version,
        OffsetDateTime createdAt, ReceiptView receipt, List<ClaimDetailDto.EvidenceDto> evidence,
        List<ClaimDetailDto.ActivityDto> activities, AppointmentView appointment, List<AnswerView> answers) {}
    public record ReviewResult(Long id, String status, String emailStatus) {}
}
