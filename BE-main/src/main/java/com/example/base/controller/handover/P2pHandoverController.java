package com.example.base.controller.handover;
import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.handover.P2pHandoverService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
@RestController @RequestMapping("/api/claims/{claimId}/handover") @RequiredArgsConstructor
public class P2pHandoverController {
    private final P2pHandoverService service;
    @PostMapping(value="/issues",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> issue(@PathVariable long claimId,@RequestPart("data") P2pHandoverService.IssueRequest data,@RequestPart(value="images",required=false) List<MultipartFile> images,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.issue(claimId,data,images,user)));
    }
    @PostMapping("/confirm") public ResponseEntity<?> confirm(@PathVariable long claimId,@RequestBody P2pHandoverService.ConfirmRequest request,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.confirm(claimId,request,user)));
    }
    @GetMapping public ResponseEntity<?> detail(@PathVariable long claimId,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(claimId,user)));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> record(@PathVariable long claimId,@RequestPart("data") P2pHandoverService.RecordRequest data,@RequestPart(value="images",required=false) List<MultipartFile> images,@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.record(claimId,data,images,user)));
    }
}
