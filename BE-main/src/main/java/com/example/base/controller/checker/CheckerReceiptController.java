package com.example.base.controller.checker;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.PageResponse;
import com.example.base.dto.checker.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.checker.CheckerReceiptService;
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

@RestController
@RequestMapping("/api/checker/receipts")
@PreAuthorize("hasRole('CHECKER')")
@RequiredArgsConstructor
@Tag(name = "Checker — Phiếu tuần tra")
public class CheckerReceiptController {
    private final CheckerReceiptService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReceiptSummaryDto>>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "ALL") String filter,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.list(page, size, keyword, filter, user)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReceiptDto>> detail(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id, user)));
    }
    @PostMapping(value = "/draft", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ReceiptDto>> create(@Valid @RequestPart("data") SaveReceiptRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(201).body(ApiResponse.ok("Đã lưu phiếu nháp", service.create(request, files, user)));
    }
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ApiResponse<ReceiptDto> update(@PathVariable long id, @Valid @RequestPart("data") SaveReceiptRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã lưu thay đổi", service.update(id, request, files, user));
    }
    @PostMapping("/{id}/submit")
    public ApiResponse<ReceiptDto> submit(@PathVariable long id, @Valid @RequestBody SubmitReceiptRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã gửi phiếu để xét duyệt", service.submit(id, request, user));
    }
    @PatchMapping("/{id}/location")
    public ApiResponse<ReceiptDto> location(@PathVariable long id, @Valid @RequestBody UpdateReceiptLocationRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok("Đã cập nhật vị trí lưu giữ", service.updateLocation(id, request, user));
    }
}
