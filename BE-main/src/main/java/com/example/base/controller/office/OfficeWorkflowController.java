package com.example.base.controller.office;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/office-workflow")
@RequiredArgsConstructor
@Tag(
        name = "11. Office Workflow & Invitations - Chuyển giao, Lịch hẹn & Trao trả",
        description = "Tiếp nhận đồ chuyển giao từ bài đăng P2P, Đặt lịch hẹn nộp/nhận đồ (Token Email 12h), Xác minh tại quầy và Lập biên bản trao trả văn phòng"
)
public class OfficeWorkflowController {
    private final OfficeTransferService transfers;
    private final OfficeReturnService returns;
    private final OfficeIntakeScheduleService intakeSchedules;
    private final OfficeInvitationService invitations;

    @GetMapping("/counter/people")
    @Operation(
            summary = "[11.1] Tìm kiếm người nhặt đồ khi họ mang đồ P2P tới nộp trực tiếp tại quầy",
            description = "Tra cứu tài khoản có bài đăng FOUND (P2P) để tiếp nhận nhanh tại quầy."
    )
    public ApiResponse<List<CounterPerson>> counterPeople(
            @RequestParam(name = "q", defaultValue = "") String q,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.counterPeople(q, user));
    }

    @GetMapping("/counter/people/{id}/posts")
    @Operation(
            summary = "[11.2] Lấy danh sách bài đăng FOUND (P2P) của một người dùng để tiếp nhận tại quầy",
            description = "Trả về các bài đăng FOUND có thể chuyển sang lưu kho văn phòng của tài khoản id."
    )
    public ApiResponse<List<CounterPost>> counterPosts(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.counterPosts(id, user));
    }

    @PostMapping("/counter/receive")
    @Operation(
            summary = "[11.3] Tiếp nhận ngay tại quầy vật phẩm từ bài đăng FOUND có sẵn vào kho Văn phòng",
            description = "Tạo biên nhận lưu kho (item_receipt) và chuyển bài đăng sang custody_mode = OFFICE."
    )
    public ApiResponse<Long> counterReceive(
            @Valid @RequestBody CounterIntake request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.receiveAtCounter(request, user));
    }

    @GetMapping("/transfers")
    @Operation(
            summary = "[11.4] Danh sách yêu cầu chuyển giao đồ cho Văn phòng (office_transfer)",
            description = "Lọc theo status (PENDING, RECEIVED, REJECTED, CANCELLED), từ khóa q và cờ mine (yêu cầu của tôi)."
    )
    public ApiResponse<PageData<TransferView>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "status", defaultValue = "PENDING") String status,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "mine", defaultValue = "false") boolean mine,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.list(page, status, q, mine, user));
    }

    @GetMapping("/transfers/{id}")
    @Operation(
            summary = "[11.5] Xem chi tiết một yêu cầu chuyển giao đồ cho Văn phòng theo ID",
            description = "Trả về thông tin bài đăng, người gửi yêu cầu, lịch hẹn nộp đồ và trạng thái đối soát."
    )
    public ApiResponse<TransferView> transfer(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.detail(id, user));
    }

    @PostMapping("/transfers/{id}/schedule")
    @Operation(
            summary = "[11.6] Đặt lịch hẹn mời người nhặt mang đồ đến nộp tại Văn phòng (kèm gửi Email)",
            description = "Tạo lịch hẹn nộp đồ (office_intake_appointment) và gửi email xác nhận có hiệu lực 12 giờ."
    )
    public ApiResponse<String> schedule(
            @PathVariable("id") long id,
            @Valid @RequestBody IntakeSchedule request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(intakeSchedules.schedule(id, request, user));
    }

    @PostMapping("/transfers/{id}/appointment-email")
    @Operation(
            summary = "[11.7] Gửi lại Email lịch hẹn nộp đồ vào Văn phòng",
            description = "Làm mới token phản hồi 12 giờ và gửi lại email mời người nhặt mang đồ đến văn phòng."
    )
    public ApiResponse<String> resendIntake(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(intakeSchedules.send(id, user));
    }

    @PostMapping("/transfers/{id}/appointment-response")
    @Operation(
            summary = "[11.8] Phản hồi lịch hẹn nộp đồ trực tiếp trên hệ thống (CONFIRMED / DECLINED)",
            description = "Người nhặt đồ xác nhận hoặc từ chối lịch hẹn nộp đồ ngay trên giao diện web."
    )
    public ApiResponse<com.example.base.dto.office.InvitationDtos.View> respondIntake(
            @PathVariable("id") long id,
            @Valid @RequestBody AppointmentResponse request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(intakeSchedules.respondOnSite(id, request, user));
    }

    @PostMapping("/transfers/{id}")
    @Operation(
            summary = "[11.9] Xử lý yêu cầu chuyển giao đồ cho Văn phòng (Tiếp nhận vào kho / Từ chối / Hủy)",
            description = "Cán bộ văn phòng xác nhận đã nhận vật phẩm vào kho (RECEIVED), từ chối (REJECTED), hoặc chủ bài hủy yêu cầu (CANCELLED)."
    )
    public ApiResponse<TransferView> decide(
            @PathVariable("id") long id,
            @Valid @RequestBody TransferDecision request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(transfers.decide(id, request, user));
    }

    @GetMapping("/claims/{id}")
    @Operation(
            summary = "[11.10] Xem chi tiết quy trình hẹn lịch, xác minh tại quầy và trao trả của một Claim Văn phòng",
            description = "Trả về thông tin lịch hẹn (appointment), kết quả xác minh tại quầy (office_claim_verification) và biên bản bàn giao (office_handover)."
    )
    public ApiResponse<Workflow> detail(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.detail(id, user));
    }

    @PostMapping("/claims/{id}/appointment-response")
    @Operation(
            summary = "[11.11] Người nhận đồ phản hồi lịch hẹn đến nhận đồ trực tiếp trên web (CONFIRMED / DECLINED)",
            description = "Xác nhận tham gia hoặc từ chối lịch hẹn nhận lại đồ tại văn phòng."
    )
    public ApiResponse<com.example.base.dto.office.InvitationDtos.View> respondCollection(
            @PathVariable("id") long id,
            @Valid @RequestBody AppointmentResponse request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(invitations.respondOnSite(id, request, user));
    }

    @PostMapping("/claims/{id}/reschedule")
    @Operation(
            summary = "[11.12] Đặt lại lịch hẹn mới (Reschedule) cho người nhận đồ tại Văn phòng",
            description = "Cập nhật thời gian/địa điểm hẹn mới và gửi lại email xác nhận cho Claimant."
    )
    public ApiResponse<ReviewResult> reschedule(
            @PathVariable("id") long id,
            @Valid @RequestBody Reschedule r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.reschedule(id, r, user));
    }

    @PostMapping("/claims/{id}/cancel")
    @Operation(
            summary = "[11.13] Hủy quy trình hẹn nhận đồ tại Văn phòng kèm lý do",
            description = "Hủy lịch hẹn và đóng/hủy hồ sơ Claim văn phòng."
    )
    public ApiResponse<Workflow> cancel(
            @PathVariable("id") long id,
            @Valid @RequestBody VersionedReason r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.cancel(id, r, user));
    }

    @PostMapping("/claims/{id}/verify")
    @Operation(
            summary = "[11.14] Ghi nhận kết quả xác minh trực tiếp tại quầy Văn phòng (PASSED / FAILED)",
            description = "Cán bộ văn phòng đối chiếu giấy tờ tùy thân và vật phẩm thực tế tại quầy trước khi bàn giao."
    )
    public ApiResponse<Workflow> verify(
            @PathVariable("id") long id,
            @Valid @RequestBody Verify r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.verify(id, r, user));
    }

    @PostMapping(value = "/claims/{id}/handover", consumes = "multipart/form-data")
    @Operation(
            summary = "[11.15] Lập biên bản xuất kho trao trả đồ tại Văn phòng kèm ảnh minh chứng",
            description = "Tạo bản ghi office_handover kèm ảnh chụp biên bản bàn giao tại quầy."
    )
    public ApiResponse<Workflow> record(
            @PathVariable("id") long id,
            @Valid @RequestPart("data") RecordHandover r,
            @RequestPart("images") List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.record(id, r, files, user));
    }

    @PostMapping("/claims/{id}/complete")
    @Operation(
            summary = "[11.16] Xác nhận hoàn tất trao trả đồ tại Văn phòng hoặc ghi nhận khiếu nại",
            description = "Hoàn tất xuất kho (item_receipt = RETURNED, post = RETURNED, claim = COMPLETED)."
    )
    public ApiResponse<Workflow> complete(
            @PathVariable("id") long id,
            @Valid @RequestBody Confirm r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(returns.complete(id, r, user));
    }
}
