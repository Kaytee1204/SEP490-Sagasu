package com.example.base.controller.approver;

import com.example.base.dto.*;
import com.example.base.dto.approver.*;
import com.example.base.dto.checker.ReceiptDto;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.approver.ApproverReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approver/receipts")
@PreAuthorize("hasRole('APPROVER')")
@RequiredArgsConstructor
public class ApproverReceiptController {
    private final ApproverReceiptService service;
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReceiptQueueDto>>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "PENDING") String filter,
            @RequestParam(defaultValue = "ALL") String source, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.list(page, size, keyword, filter, source, user)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReceiptDto>> detail(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id, user)));
    }
    @PostMapping("/{id}/review")
    public ApiResponse<ReceiptDto> review(@PathVariable long id, @Valid @RequestBody ReviewReceiptRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã lưu kết quả xét duyệt", service.review(id, request, user));
    }
}
