package com.example.base.controller.claim;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.claim.ClaimQuestionnaireDto;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.ClaimService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'APPROVER', 'MANAGER', 'ADMIN')")
@Tag(name = "Claims", description = "Bộ câu hỏi xác minh nhận đồ theo danh mục")
public class ClaimQuestionnaireController {
    private final ClaimService service;

    @GetMapping("/posts/{postId}/claims/questions")
    public ResponseEntity<ApiResponse<ClaimQuestionnaireDto>> questions(
            @PathVariable long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.questionnaire(postId, user)));
    }
}
