package com.example.base.controller.office;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.service.office.OfficeInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/office-invitations")
@RequiredArgsConstructor
@Tag(
        name = "11. Office Workflow & Invitations - Chuyển giao, Lịch hẹn & Trao trả",
        description = "Tiếp nhận đồ chuyển giao từ bài đăng P2P, Đặt lịch hẹn nộp/nhận đồ (Token Email 12h), Xác minh tại quầy và Lập biên bản trao trả văn phòng"
)
public class OfficeInvitationController {
    private final OfficeInvitationService service;

    @PostMapping("/preview")
    @Operation(
            summary = "[11.17] Xem thông tin lịch hẹn nhận đồ tại Văn phòng qua mã Token từ Email (Public)",
            description = "Kiểm tra thông tin lịch hẹn đến nhận đồ khi người dùng bấm vào đường dẫn trong Email."
    )
    public ResponseEntity<ApiResponse<View>> preview(@Valid @RequestBody Preview request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.preview(request.token())));
    }

    @PostMapping("/respond")
    @Operation(
            summary = "[11.18] Xác nhận hoặc Từ chối lịch hẹn nhận đồ qua mã Token Email (trong hạn 12 giờ)",
            description = "Cập nhật trạng thái phản hồi lịch hẹn nhận đồ (CONFIRMED / DECLINED) từ liên kết trong Email."
    )
    public ResponseEntity<ApiResponse<View>> respond(@Valid @RequestBody Response request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.respond(request)));
    }
}
