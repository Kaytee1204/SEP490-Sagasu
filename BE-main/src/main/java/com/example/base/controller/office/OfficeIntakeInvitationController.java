package com.example.base.controller.office;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.service.office.OfficeIntakeScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/office-intake-invitations")
@RequiredArgsConstructor
@Tag(
        name = "11. Office Workflow & Invitations - Chuyển giao, Lịch hẹn & Trao trả",
        description = "Tiếp nhận đồ chuyển giao từ bài đăng P2P, Đặt lịch hẹn nộp/nhận đồ (Token Email 12h), Xác minh tại quầy và Lập biên bản trao trả văn phòng"
)
public class OfficeIntakeInvitationController {
    private final OfficeIntakeScheduleService service;

    @PostMapping("/preview")
    @Operation(
            summary = "[11.19] Xem thông tin lịch hẹn mang đồ đến nộp cho Văn phòng qua mã Token Email (Public)",
            description = "Hiển thị chi tiết lịch hẹn giao nộp vật phẩm cho văn phòng từ liên kết trong Email."
    )
    public ApiResponse<View> preview(@Valid @RequestBody Preview r) {
        return ApiResponse.ok(service.preview(r.token()));
    }

    @PostMapping("/respond")
    @Operation(
            summary = "[11.20] Xác nhận hoặc Từ chối lịch hẹn nộp đồ vào Văn phòng qua mã Token Email (12h)",
            description = "Người nhặt đồ phản hồi đồng ý hoặc từ chối lịch hẹn mang đồ đến nộp cho văn phòng."
    )
    public ApiResponse<View> respond(@Valid @RequestBody Response r) {
        return ApiResponse.ok(service.respond(r));
    }
}
