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
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/office-claims")
@RequiredArgsConstructor
@Tag(
        name = "10. Office Claims - Tiếp nhận & Xét duyệt Claim tại Văn phòng",
        description = "Nộp Claim tại quầy hoặc trực tuyến cho đồ lưu kho văn phòng, Xét duyệt sơ bộ, Yêu cầu bổ sung và Gửi lại email lịch hẹn"
)
public class OfficeClaimController {

    private final OfficeClaimService service;
    private final com.example.base.service.claim.OfficeSupplementService supplements;

    @GetMapping("/{id}/supplements")
    @Operation(
            summary = "[10.12] Xem lịch sử các lần gửi bổ sung thông tin và ảnh bằng chứng của Claim Văn phòng",
            description = "Trả về danh sách các lần bổ sung thông tin (RESUBMIT) kèm câu hỏi yêu cầu và ảnh đính kèm."
    )
    public ApiResponse<?> supplements(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(supplements.history(id, user));
    }

    @GetMapping("/questionnaire")
    @Operation(
            summary = "[10.10] Lấy bảng câu hỏi xác minh (Questionnaire) khi lập Claim Văn phòng",
            description = "Trả về bộ câu hỏi xác minh theo danh mục của bài đăng khi nộp Claim trực tuyến hoặc tại quầy."
    )
    public ApiResponse<com.example.base.dto.claim.ClaimQuestionnaireDto> questionnaire(
            @RequestParam(name="postId") long postId,
            @RequestParam(name="counter",defaultValue="false") boolean counter,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.questionnaire(postId,counter,user));
    }

    @GetMapping("/counter/items")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Tìm tài sản còn nhận yêu cầu tại quầy theo tên hoặc mã phiếu tiếp nhận")
    public ApiResponse<List<CounterItemOption>> counterItems(
            @RequestParam(name = "q") String keyword,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.findCounterItems(keyword, user));
    }

    @GetMapping("/counter/items/{postId}")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Xem trước tài sản được chọn khi lập yêu cầu nhận đồ tại quầy")
    public ApiResponse<CounterItemPreview> counterItem(
            @PathVariable("postId") long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.counterItem(postId, user));
    }

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

    @GetMapping("/groups")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Danh sách yêu cầu nhận tài sản văn phòng theo bài đăng")
    public ApiResponse<GroupedClaims> groups(
            @RequestParam(name="page",defaultValue="1") int page,
            @RequestParam(name="status",defaultValue="ALL") String status,
            @RequestParam(name="q",defaultValue="") String q,
            @RequestParam(name="stage",defaultValue="ALL") String stage,
            @RequestParam(name="channel",defaultValue="ALL") String channel,
            @RequestParam(name="since",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(name="oldest",defaultValue="false") boolean oldest,
            @Parameter(hidden=true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.groups(page,status,q,stage,channel,since,oldest,user));
    }

    @GetMapping("/posts/{postId}")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Danh sách yêu cầu của một bài đăng tại văn phòng")
    public ApiResponse<PageData<ClaimView>> forPost(
            @PathVariable("postId") long postId,
            @RequestParam(name="page",defaultValue="1") int page,
            @RequestParam(name="status",defaultValue="ALL") String status,
            @RequestParam(name="q",defaultValue="") String q,
            @RequestParam(name="stage",defaultValue="ALL") String stage,
            @RequestParam(name="channel",defaultValue="ALL") String channel,
            @RequestParam(name="since",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(name="oldest",defaultValue="false") boolean oldest,
            @Parameter(hidden=true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.forPost(postId,page,status,q,stage,channel,since,oldest,user));
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

    @GetMapping("/mine/{id}")
    @Operation(
            summary = "[10.13] Xem chi tiết hồ sơ Claim Văn phòng của chính tài khoản đăng nhập (kể cả Approver)",
            description = "Dành cho người dùng hoặc Approver xem hồ sơ Claim văn phòng do chính mình đứng tên nhận đồ."
    )
    public ApiResponse<ClaimView> personalDetail(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.personalDetail(id, user));
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
            description = "Khi lịch nhận đồ được lưu, người nhận có 12 giờ để phản hồi trên web hoặc qua liên kết email."
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
            description = "Gửi lại email kèm liên kết mới, giữ nguyên hạn phản hồi của lịch đã mở."
    )
    public ApiResponse<ReviewResult> resend(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.resend(id, user));
    }

}
