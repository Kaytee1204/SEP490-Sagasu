package com.example.base.dto.office;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OfficeWorkflowDtos {
    private OfficeWorkflowDtos() {}
    public record AppointmentResponse(@NotNull @Positive Long appointmentId,
            @NotBlank @Pattern(regexp="CONFIRM|DECLINE") String decision) {}
    public record TransferDecision(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Pattern(regexp="RECEIVED|REJECTED|CANCELLED") String decision,
            Long locationId, @Size(max=2000) String condition, @Size(max=2000) String reason,
            @Size(max=2000) String reconciliationNote) {}
    public record TransferView(long id, long postId, String title, String description, String finder,
            String status, OffsetDateTime requestedAt, OffsetDateTime processedAt,
            String reason, String reconciliationNote, Long receiptId, long version,
            long activeClaims, long previousHandovers, List<String> images, IntakeAppointment appointment, long requestedBy) {}
    public record IntakeAppointment(long id, OffsetDateTime scheduledAt, String location, String note,
            String status, String emailStatus, String responseStatus, OffsetDateTime responseDeadline) {}
    public record IntakeSchedule(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull OffsetDateTime scheduledAt, @NotBlank @Size(max=1000) String location,
            @Size(max=2000) String note, @Size(max=2000) String reason) {}
    public record VersionedReason(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max=2000) String reason) {}
    public record Reschedule(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull OffsetDateTime scheduledAt, @NotBlank @Size(max=1000) String location,
            @NotBlank @Size(max=2000) String reason, @Size(max=2000) String note) {}
    public record Verify(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Pattern(regexp="PASSED|FAILED") String result,
            @NotBlank @Size(max=2000) String note) {}
    public record RecordHandover(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull UUID clientRequestId, @NotBlank @Size(max=2000) String condition) {}
    public record Confirm(@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Verification(long id, long appointmentId, String result, String note,
            String officer, OffsetDateTime at) {}
    public record Evidence(long id, String url, String name) {}
    public record Handover(long id, String status, String condition, String officer, String recipient,
            OffsetDateTime recordedAt, OffsetDateTime confirmedAt, String issueNote, long version,
            List<Evidence> evidence) {}
    public record Workflow(List<Verification> verifications, Handover handover,
            boolean canVerify, boolean canRecord, boolean canReschedule, boolean canCancel,
            boolean canComplete, String nextStep) {}
}
