package com.example.base.controller.receipt;

import com.example.base.dto.ApiResponse;
import com.example.base.service.receipt.ReceiptLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/storage-locations")
@PreAuthorize("hasRole('APPROVER')")
@Tag(name = "6. Receipt Storage Locations", description = "Danh mục kho và vị trí lưu trữ tài sản tiếp nhận")
public class ReceiptLocationController {
    private final ReceiptLocationService service;

    public ReceiptLocationController(ReceiptLocationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các kho / vị trí lưu trữ đang hoạt động",
            description = "Dành cho Cán bộ tiếp nhận (APPROVER) lựa chọn vị trí kho lưu đồ khi lập hoặc cập nhật biên bản tiếp nhận.")
    public ResponseEntity<ApiResponse<List<ReceiptLocationService.Option>>> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.options()));
    }
}
