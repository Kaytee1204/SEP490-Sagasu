package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.claim.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.ClaimService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
@Tag(name = "Claims P2P", description = "UC40-45: Hồ sơ nhận đồ riêng tư giữa người claim và Finder")
public class ClaimController {
    private final ClaimService service;

    @PostMapping(value = "/posts/{postId}/claims", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ClaimResultDto>> submit(@PathVariable long postId,
            @Valid @RequestPart("data") SubmitClaimRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.submit(postId, request, images, user));
    }
    @PostMapping(value = "/claims/{id}/retry", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ClaimResultDto>> retry(@PathVariable long id,
            @Valid @RequestPart("data") SubmitClaimRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(201, service.retry(id, request, images, user));
    }
    @GetMapping("/posts/{postId}/claims/questions")
    public ResponseEntity<ApiResponse<ClaimQuestionnaireDto>> questions(@PathVariable long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.questionnaire(postId, user));
    }

    @GetMapping("/claims/mine")
    public ResponseEntity<ApiResponse<ClaimPageDto>> mine(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size, @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.mine(page, size, status, user));
    }
    @GetMapping("/posts/{postId}/claims/mine")
    public ResponseEntity<ApiResponse<ClaimResultDto>> latestOnPost(@PathVariable long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.latestOnPost(postId, user));
    }
    @GetMapping("/claims/received")
    public ResponseEntity<ApiResponse<ClaimPageDto>> received(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size, @RequestParam(defaultValue = "ALL") String status,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.received(page, size, status, user));
    }
    @GetMapping("/posts/{postId}/claims")
    public ResponseEntity<ApiResponse<ClaimPageDto>> onPost(@PathVariable long postId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String status, @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.onPost(postId, page, size, status, user));
    }
    @GetMapping("/claims/{id}")
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detail(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detail(id, user));
    }
    @GetMapping("/posts/{postId}/claims/{id}")
    public ResponseEntity<ApiResponse<ClaimDetailDto>> detailOnPost(@PathVariable long postId, @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.detailOnPost(postId, id, user));
    }
    @PostMapping("/claims/{id}/review")
    public ResponseEntity<ApiResponse<ClaimResultDto>> review(@PathVariable long id,
            @Valid @RequestBody ReviewClaimRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.review(id, request, user));
    }
    @PostMapping("/claims/{id}/cancel")
    public ResponseEntity<ApiResponse<ClaimResultDto>> cancel(@PathVariable long id,
            @Valid @RequestBody CancelClaimRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return response(200, service.cancel(id, request, user));
    }
    private <T> ResponseEntity<ApiResponse<T>> response(int status, T data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
