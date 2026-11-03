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
@Tag(name = "8. Claims P2P", description = "UC40-45: Hồ sơ nhận đồ riêng tư giữa người claim và Finder")
public class ClaimController {
    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @PostMapping(value = "/posts/{postId}/claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "UC40: Gửi yêu cầu nhận đồ (Claim) kèm bằng chứng hình ảnh")
    public ResponseEntity<ApiResponse<ClaimResultDto>> submit(
            @PathVariable long postId,
            @Parameter(description = "Thông tin yêu cầu nhận đồ dạng JSON") @Valid @RequestPart("data") SubmitClaimRequest request,
            @Parameter(description = "Ảnh minh chứng (tùy chọn)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.submit(postId, request, images, user));
    }

    @PostMapping(value = "/claims/{id}/retry", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "UC41: Gửi lại yêu cầu nhận đồ sau khi đã hủy hoặc cần bổ sung")
    public ResponseEntity<ApiResponse<ClaimResultDto>> retry(
            @PathVariable long id,
            @Parameter(description = "Thông tin yêu cầu nhận đồ cập nhật dạng JSON") @Valid @RequestPart("data") SubmitClaimRequest request,
            @Parameter(description = "Ảnh minh chứng mới (tùy chọn)") @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.retry(id, request, images, user));
    }

    @GetMapping("/claims/mine")
    @Operation(summary = "UC42: Danh sách yêu cầu nhận đồ do tôi đã gửi")
    public ResponseEntity<ApiResponse<ClaimPageDto>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.mine(page, size, status, user));
    }

    @GetMapping("/posts/{postId}/claims/mine")
    @Operation(summary = "Xem yêu cầu nhận đồ gần nhất của tôi trên một bài đăng cụ thể")
    public ResponseEntity<ApiResponse<ClaimResultDto>> latestOnPost(
            @PathVariable long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.latestOnPost(postId, user));
    }

    @GetMapping("/claims/received")
    @Operation(summary = "UC43: Danh sách yêu cầu nhận đồ gửi tới các bài đăng của tôi")
    public ResponseEntity<ApiResponse<ClaimPageDto>> received(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.received(page, size, status, user));
    }

    @GetMapping("/posts/{postId}/claims")
    @Operation(summary = "Danh sách yêu cầu nhận đồ của một bài đăng cụ thể")
    public ResponseEntity<ApiResponse<ClaimPageDto>> onPost(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.onPost(postId, page, size, status, user));
    }

    @GetMapping("/claims/{id}")
    @Operation(summary = "Xem chi tiết một hồ sơ nhận đồ theo ID")
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detail(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detail(id, user));
    }

    @GetMapping("/posts/{postId}/claims/{id}")
    @Operation(summary = "Xem chi tiết hồ sơ nhận đồ theo bài đăng và ID")
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detailOnPost(
            @PathVariable long postId,
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detailOnPost(postId, id, user));
    }

    @PostMapping("/claims/{id}/review")
    @Operation(summary = "UC44: Người đăng bài duyệt (Approve / Reject) yêu cầu nhận đồ")
    public ResponseEntity<ApiResponse<ClaimResultDto>> review(
            @PathVariable long id,
            @Valid @RequestBody ReviewClaimRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.review(id, request, user));
    }

    @PostMapping("/claims/{id}/cancel")
    @Operation(summary = "UC45: Người gửi hủy yêu cầu nhận đồ")
    public ResponseEntity<ApiResponse<ClaimResultDto>> cancel(
            @PathVariable long id,
            @Valid @RequestBody CancelClaimRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.cancel(id, request, user));
    }

    private <T> ResponseEntity<ApiResponse<T>> response(int status, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
