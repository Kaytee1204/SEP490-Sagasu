package com.example.base.controller.claim;

import com.example.base.service.claim.OfficeClaimService;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DaiHV: Controller cung cap cac API tiep nhan va xu ly Claim cho tai san tai Van phong.
 */
@RestController
@RequestMapping("/api/office-claims")
@RequiredArgsConstructor
@Tag(name = "9. Office Claims", description = "Tiep nhan va xu ly yeu cau nhan lai do that lac tai Van phong (DaiHV)")
public class OfficeClaimController {

    private final OfficeClaimService service;
    @GetMapping("/claimants") @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<ClaimantOption>> claimants(@RequestParam String q,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.findClaimants(q,user)); }
    @GetMapping @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<PageData<ClaimView>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String q,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.list(page,status,q,false,user)); }
    @GetMapping("/mine") public ApiResponse<PageData<ClaimView>> mine(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String q,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.list(page,status,q,true,user)); }
    @GetMapping("/{id}") public ApiResponse<ClaimView> detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.detail(id,user)); }
    @PostMapping(value="/counter",consumes="multipart/form-data") @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Long> counter(@Valid @RequestPart("data") Submission request,@RequestPart(value="images",required=false) List<MultipartFile> files,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.submit(request,files,true,user)); }
    @PostMapping(consumes="multipart/form-data") public ApiResponse<Long> online(@Valid @RequestPart("data") Submission request,@RequestPart(value="images",required=false) List<MultipartFile> files,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.submit(request,files,false,user)); }
    @PostMapping("/{id}/review") @PreAuthorize("hasRole('MANAGER')") public ApiResponse<ReviewResult> review(@PathVariable long id,@Valid @RequestBody Decision r,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.review(id,r,user)); }
    @PostMapping("/{id}/appointment-email") @PreAuthorize("hasRole('MANAGER')") public ApiResponse<ReviewResult> resend(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.resend(id,user)); }
    @PostMapping("/{id}/supplement") public ApiResponse<Void> supplement(@PathVariable long id,@Valid @RequestBody Supplement r,@AuthenticationPrincipal CustomUserDetails user) { service.supplement(id,r,user); return ApiResponse.ok(null); }
}

