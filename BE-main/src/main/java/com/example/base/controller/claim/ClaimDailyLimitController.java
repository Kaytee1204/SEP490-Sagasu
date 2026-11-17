package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.claim.ClaimDailyLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

@RestController
@RequiredArgsConstructor
public class ClaimDailyLimitController {
    private final ClaimDailyLimit limit;
    private final ChatAccessPolicy access;

    @GetMapping("/api/claims/daily-limit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClaimDailyLimit.Status>> status(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(limit.status(access.actor(user).getId())));
    }
}
