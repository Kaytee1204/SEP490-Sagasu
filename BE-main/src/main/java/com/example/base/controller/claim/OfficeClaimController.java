package com.example.base.controller.claim;

import com.example.base.service.claim.OfficeClaimService;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/office-claims")
@RequiredArgsConstructor
@Tag(
        name = "10. Office Claims - Tiếp nhận & Xét duyệt Claim tại Văn phòng",
        description = "Nộp Claim tại quầy hoặc trực tuyến cho đồ lưu kho văn phòng, Xét duyệt sơ bộ, Yêu cầu bổ sung và Gửi lại email lịch hẹn"
)
public class OfficeClaimController {

    private final OfficeClaimService service;

    @GetMapping("/claimants")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[10.1] Tìm kiếm tài khoản người nhận đồ (Claimant) khi lập Claim tại quầy",
            description = "Dành cho APPROVER tìm kiếm người dùng theo tên/email/số điện thoại khi hỗ trợ nộp Claim tại quầy."
    )
    public ApiResponse<List<ClaimantOption>> claimants(
            @RequestParam(name = "q") String q,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.findClaimants(q, user));
    }

    @GetMapping
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[10.2] Danh sách toàn bộ Claim đối với đồ đang lưu kho Văn phòng (APPROVER)",
            description = "Lọc theo status (ALL, PENDING, NEEDS_INFO, APPROVED, REJECTED, COMPLETED), stage và từ khóa q."
    )
    public ApiResponse<PageData<ClaimView>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "stage", defaultValue = "ALL") String stage,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.list(page, status, q, stage, false, user));
    }

    @GetMapping("/mine")
    @Operation(
            summary = "[10.3] Danh sách Claim Văn phòng do tôi đã nộp",
            description = "Dành cho người dùng theo dõi tiến độ các yêu cầu nhận đồ lưu kho tại văn phòng của mình."
    )
    public ApiResponse<PageData<ClaimView>> mine(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "stage", defaultValue = "ALL") String stage,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.list(page, status, q, stage, true, user));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "[10.4] Xem chi tiết hồ sơ Claim Văn phòng theo ID",
            description = "Trả về chi tiết yêu cầu nhận đồ văn phòng, câu trả lời xác minh, lịch hẹn và lịch sử xử lý."
    )
    public ApiResponse<ClaimView> detail(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.detail(id, user));
    }

    @PostMapping(value = "/counter", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[10.5] Cán bộ tiếp nhận lập hồ sơ Claim trực tiếp tại quầy (COUNTER)",
            description = "Dành cho APPROVER nhập thông tin người nhận đến trực tiếp tại quầy văn phòng."
    )
    public ApiResponse<Long> counter(
            @Valid @RequestPart("data") Submission request,
            @RequestPart(value = "images", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.submit(request, files, true, user));
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(
            summary = "[10.6] Người dùng gửi hồ sơ Claim trực tuyến cho đồ đang lưu tại Văn phòng (ONLINE)",
            description = "Nộp yêu cầu nhận lại đồ thuộc diện quản lý của Văn phòng (custody_mode = OFFICE)."
    )
    public ApiResponse<Long> online(
            @Valid @RequestPart("data") Submission request,
            @RequestPart(value = "images", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.submit(request, files, false, user));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[10.7] Cán bộ văn phòng xét duyệt Claim (APPROVE kèm đặt lịch hẹn nhận đồ / REJECT / NEEDS_INFO)",
            description = "Khi APPROVE sẽ tạo lịch hẹn (appointment) và gửi Email kèm link xác nhận trong 12 giờ cho người nhận."
    )
    public ApiResponse<ReviewResult> review(
            @PathVariable("id") long id,
            @Valid @RequestBody Decision r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.review(id, r, user));
    }

    @PostMapping("/{id}/appointment-email")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[10.8] Gửi lại Email mời xác nhận lịch hẹn nhận đồ tại Văn phòng",
            description = "Tạo token mới (hiệu lực 12 giờ) và gửi lại email lịch hẹn đến nhận đồ cho Claimant."
    )
    public ApiResponse<ReviewResult> resend(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.resend(id, user));
    }

    @PostMapping("/{id}/supplement")
    @Operation(
            summary = "[10.9] Người nhận gửi bổ sung thông tin cho Claim Văn phòng (khi ở trạng thái NEEDS_INFO)",
            description = "Cập nhật thông tin bổ sung theo yêu cầu của cán bộ văn phòng và chuyển lại trạng thái sang PENDING."
    )
    public ApiResponse<Void> supplement(
            @PathVariable("id") long id,
            @Valid @RequestBody Supplement r,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        service.supplement(id, r, user);
        return ApiResponse.ok(null);
    }
}
