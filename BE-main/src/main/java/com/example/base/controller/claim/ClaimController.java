package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.claim.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
@Tag(
        name = "06. Claims (P2P) - Yêu cầu nhận đồ & Bộ câu hỏi xác minh",
        description = "Lấy bộ câu hỏi xác minh theo danh mục, Gửi/Gửi lại Claim, Gom nhóm Claim theo bài đăng, Duyệt/Từ chối/Yêu cầu bổ sung và Hủy Claim"
)
public class ClaimController {
    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @PostMapping(value = "/posts/{postId}/claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[6.2] UC40: Gửi yêu cầu nhận lại đồ (Claim P2P) kèm câu trả lời xác minh và ảnh bằng chứng",
            description = "Người mất đồ nộp Claim trên bài đăng FOUND kèm câu trả lời bộ câu hỏi (answers) và ảnh minh chứng (images)."
    )
    public ResponseEntity<ApiResponse<ClaimResultDto>> submit(
            @PathVariable long postId,
            @Parameter(description = "Thông tin yêu cầu nhận đồ dạng JSON") @Valid @RequestPart("data") SubmitClaimRequest request,
            @Parameter(description = "Ảnh minh chứng (tùy chọn)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.submit(postId, request, images, user));
    }

    @PostMapping(value = "/claims/{id}/retry", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[6.3] UC41: Gửi lại / Bổ sung thông tin yêu cầu nhận đồ (khi NEEDS_INFO hoặc sau khi hủy)",
            description = "Cập nhật lại câu trả lời và bằng chứng cho yêu cầu nhận đồ."
    )
    public ResponseEntity<ApiResponse<ClaimResultDto>> retry(
            @PathVariable long id,
            @Parameter(description = "Thông tin yêu cầu nhận đồ cập nhật dạng JSON") @Valid @RequestPart("data") SubmitClaimRequest request,
            @Parameter(description = "Ảnh minh chứng mới (tùy chọn)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.retry(id, request, images, user));
    }

    @GetMapping("/claims/grouped")
    @Operation(
            summary = "[6.4] Danh sách yêu cầu nhận đồ được gom nhóm theo từng Bài đăng (Đã gửi hoặc Nhận được)",
            description = "Truyền sent = true để xem các Claim do tôi gửi, hoặc sent = false để xem các Claim gửi tới bài đăng của tôi (gom nhóm theo bài đăng)."
    )
    public ResponseEntity<ApiResponse<ClaimPostGroupDto.PageData>> grouped(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "false") boolean sent,
            @RequestParam(required = false) Long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.grouped(page, size, status, sent, postId, user));
    }

    @GetMapping("/claims/mine")
    @Operation(
            summary = "[6.5] UC42: Danh sách yêu cầu nhận đồ do tôi đã gửi (phân trang)",
            description = "Lọc theo trạng thái status (ALL, PENDING, NEEDS_INFO, APPROVED, REJECTED, CANCELLED, COMPLETED, CLOSED)."
    )
    public ResponseEntity<ApiResponse<ClaimPageDto>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.mine(page, size, status, user));
    }

    @GetMapping("/posts/{postId}/claims/mine")
    @Operation(
            summary = "[6.6] Xem yêu cầu nhận đồ gần nhất của tôi trên một bài đăng cụ thể",
            description = "Kiểm tra nhanh trạng thái Claim của người dùng hiện tại đối với bài đăng postId."
    )
    public ResponseEntity<ApiResponse<ClaimResultDto>> latestOnPost(
            @PathVariable long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.latestOnPost(postId, user));
    }

    @GetMapping("/claims/received")
    @Operation(
            summary = "[6.7] UC43: Danh sách yêu cầu nhận đồ gửi tới các bài đăng của tôi",
            description = "Dành cho người nhặt đồ (Finder) xem tất cả các Claim gửi tới bài đăng của mình."
    )
    public ResponseEntity<ApiResponse<ClaimPageDto>> received(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.received(page, size, status, user));
    }

    @GetMapping("/posts/{postId}/claims")
    @Operation(
            summary = "[6.8] Danh sách tất cả yêu cầu nhận đồ của một bài đăng cụ thể",
            description = "Dành cho chủ bài đăng xem toàn bộ Claim của bài đăng postId."
    )
    public ResponseEntity<ApiResponse<ClaimPageDto>> onPost(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.onPost(postId, page, size, status, user));
    }

    @GetMapping("/claims/{id}")
    @Operation(
            summary = "[6.9] Xem chi tiết một hồ sơ nhận đồ (Claim) theo ID",
            description = "Trả về chi tiết câu trả lời xác minh, danh sách ảnh bằng chứng, lịch sử hoạt động (claim_activity) và thông tin phòng chat/bàn giao."
    )
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detail(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detail(id, user));
    }

    @GetMapping("/posts/{postId}/claims/{id}")
    @Operation(
            summary = "[6.10] Xem chi tiết hồ sơ nhận đồ theo postId và claimId",
            description = "Trả về chi tiết Claim kiểm tra khớp với postId."
    )
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detailOnPost(
            @PathVariable long postId,
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detailOnPost(postId, id, user));
    }

    @PostMapping("/claims/{id}/review")
    @Operation(
            summary = "[6.11] UC44: Chủ bài đăng xét duyệt yêu cầu nhận đồ (APPROVE / REJECT / NEEDS_INFO)",
            description = "Khi APPROVE: bài đăng chuyển sang RESERVED và tự động mở phòng Chat P2P giữa 2 bên."
    )
    public ResponseEntity<ApiResponse<ClaimResultDto>> review(
            @PathVariable long id,
            @Valid @RequestBody ReviewClaimRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.review(id, request, user));
    }

    @PostMapping("/claims/{id}/cancel")
    @Operation(
            summary = "[6.12] UC45: Người gửi chủ động hủy yêu cầu nhận đồ kèm lý do",
            description = "Chuyển trạng thái Claim sang CANCELLED."
    )
    public ResponseEntity<ApiResponse<ClaimResultDto>> cancel(
            @PathVariable long id,
            @Valid @RequestBody CancelClaimRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.cancel(id, request, user));
    }

    @GetMapping("/claims/office")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(
            summary = "[6.13] Danh sách Claim thuộc diện xử lý tại Văn phòng (APPROVER)",
            description = "Dành cho Cán bộ tiếp nhận (APPROVER) tra cứu các yêu cầu nhận đồ do văn phòng quản lý."
    )
    public ResponseEntity<ApiResponse<ClaimPageDto>> office(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.office(page, size, status, user));
    }

    private <T> ResponseEntity<ApiResponse<T>> response(int status, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
