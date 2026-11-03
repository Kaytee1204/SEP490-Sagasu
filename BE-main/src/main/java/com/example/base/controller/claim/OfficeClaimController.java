package com.example.base.controller.claim;

import com.example.base.service.claim.OfficeClaimService;
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

}

