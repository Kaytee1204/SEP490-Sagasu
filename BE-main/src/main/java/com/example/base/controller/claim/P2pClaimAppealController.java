package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.P2pClaimAppealService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/claims/{claimId}/appeal")
@RequiredArgsConstructor
public class P2pClaimAppealController {
    private final P2pClaimAppealService appeals;

    @GetMapping
    @Operation(summary = "Xem thời hạn và trạng thái tranh chấp của yêu cầu nhận tài sản P2P hoặc tại văn phòng")
    public ResponseEntity<ApiResponse<P2pClaimAppealService.Status>> status(@PathVariable long claimId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(appeals.status(claimId, user)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Gửi tranh chấp kết quả xét duyệt yêu cầu nhận tài sản P2P hoặc tại văn phòng")
    public ResponseEntity<ApiResponse<P2pClaimAppealService.Submitted>> submit(@PathVariable long claimId,
            @RequestPart("data") P2pClaimAppealService.Submit request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(appeals.submit(claimId, request, images, user)));
    }
}
