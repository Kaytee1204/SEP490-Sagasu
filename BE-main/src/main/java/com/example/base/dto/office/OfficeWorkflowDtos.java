package com.example.base.dto.office;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class OfficeWorkflowDtos {
    private OfficeWorkflowDtos() {}
    public record CounterPerson(long id,String name,String email) {}
    public record CounterPost(long id,String title,String description,List<String> images,long previousHandovers) {}
    public record CounterIntake(@NotNull @Positive Long accountId,@NotNull @Positive Long postId,
            @NotNull @Positive Long locationId,@NotBlank @Size(max=2000) String condition,
            @Size(max=2000) String reconciliationNote,@AssertTrue boolean physicallyReceived,
            @NotBlank(message="Vui lòng ghi danh sách tài sản và phụ kiện tiếp nhận.") @Size(max=2000) String items) {}
    public record AppointmentResponse(@NotNull @Positive Long appointmentId,
            @NotBlank @Pattern(regexp="CONFIRM|DECLINE") String decision,@Size(max=2000) String reason) {}
    public record TransferDecision(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Pattern(regexp="RECEIVED|REJECTED|CANCELLED") String decision,
            Long locationId, @Size(max=2000) String condition, @Size(max=2000) String reason,
            @Size(max=2000) String reconciliationNote, @Size(max=2000) String items) {}
    public record TransferView(long id, long postId, String title, String description, String finder,
            String status, OffsetDateTime requestedAt, OffsetDateTime processedAt,
            String reason, String reconciliationNote, Long receiptId, long version,
            long activeClaims, long previousHandovers, List<String> images, IntakeAppointment appointment, long requestedBy,
            List<String> receiptEvidence, String receiptItems, String receiptCondition) {}
    public record IntakeAppointment(long id, OffsetDateTime scheduledAt, String location, String note,
            String status, String emailStatus, OffsetDateTime intakeDeadline, String cancellationReason) {}
    public record IntakeSchedule(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull OffsetDateTime scheduledAt, @NotBlank @Size(max=1000) String location,
            @Size(max=2000) String note, @Size(max=2000) String reason) {}
    public record VersionedReason(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max=2000) String reason) {}
    public record Reschedule(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull OffsetDateTime scheduledAt, @NotBlank @Size(max=1000) String location,
            @NotBlank @Size(max=2000) String reason, @Size(max=2000) String note) {}
    public record Schedule(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull OffsetDateTime scheduledAt, @NotBlank @Size(max=1000) String location,
            @jakarta.validation.constraints.Email @NotBlank @Size(max=255) String email,
            @Size(max=2000) String note) {}
    public record Verify(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Pattern(regexp="PASSED|FAILED") String result,
            @NotBlank @Size(max=2000) String note) {}
    public record RecordHandover(@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull UUID clientRequestId, @NotBlank @Size(max=2000) String items,
            @NotBlank @Size(max=2000) String condition, @Size(max=2000) String differenceNote,
            @JsonAlias("physicallyHandedOver")
            @AssertTrue(message="Vui lòng xác nhận đã đối chiếu tài sản tại quầy.") boolean assetsCheckedAtCounter) {}
    public record Confirm(@NotNull @Positive Long handoverId, @NotNull @PositiveOrZero Long expectedVersion,
            @AssertTrue(message="Vui lòng xác nhận nội dung biên bản.") boolean acknowledged) {}
    public record ReopenConfirmation(@NotNull @Positive Long handoverId, @NotNull @PositiveOrZero Long expectedVersion,
            @AssertTrue(message="Vui lòng xác nhận người nhận đang có mặt tại quầy.") boolean recipientPresent) {}
    public record StopHandover(@NotNull @Positive Long handoverId, @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max=2000) String reason,
            @AssertTrue(message="Vui lòng xác nhận tài sản vẫn do văn phòng lưu giữ.") boolean itemAtOffice) {}
    public record Verification(long id, long appointmentId, String result, String note,
            String officer, OffsetDateTime at) {}
    public record Evidence(long id, String url, String name) {}
    public record Handover(long id, String status, String condition, String officer, String recipient,
            OffsetDateTime recordedAt, OffsetDateTime confirmedAt, String issueNote, long version,
            List<Evidence> evidence, String items, String differenceNote, OffsetDateTime confirmationDeadline,
            String completionMethod) {}
    public record FiledAppeal(long reportId, String status) {}
    public record Workflow(List<Verification> verifications, Handover handover,
            boolean canVerify, boolean canRecord, boolean canReschedule, boolean canCancel,
            String nextStep, boolean canSchedule, boolean canRevoke,
            boolean approvalHasCompetitors, OffsetDateTime appealDeadline, String appealState, FiledAppeal filedAppeal,
            OffsetDateTime verificationDeadline, OffsetDateTime reportDeadline,
            boolean counterConfirmationOpen, boolean canConfirmReceipt, boolean canReopenConfirmation,
            boolean canStopHandover, Long openHandoverReportId) {}
}
