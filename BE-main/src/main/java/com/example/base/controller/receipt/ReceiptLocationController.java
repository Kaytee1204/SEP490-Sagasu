package com.example.base.controller.receipt;

import com.example.base.dto.ApiResponse;
import com.example.base.service.receipt.ReceiptLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/storage-locations")
@PreAuthorize("hasAnyRole('CHECKER', 'APPROVER')")
@RequiredArgsConstructor
public class ReceiptLocationController {
    private final ReceiptLocationService service;
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReceiptLocationService.Option>>> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.options()));
    }
}
