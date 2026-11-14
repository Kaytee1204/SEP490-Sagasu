package com.example.base.controller.chat;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.ChatPlaceDto;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats/{id}/locations/address")
@RequiredArgsConstructor
@Tag(
        name = "07. Chat (P2P) - Trò chuyện & Chia sẻ vị trí",
        description = "Mở phòng chat P2P, Lịch sử tin nhắn, Gửi tin nhắn văn bản/hình ảnh/tài liệu, Chia sẻ vị trí bản đồ và Đánh dấu đã đọc"
)
public class ChatLocationAddressController {
    private final ChatService chats;
    private final ChatLocationAddressService addresses;

    @GetMapping
    @Operation(
            summary = "[7.13] Tra cứu địa chỉ văn bản từ tọa độ bản đồ (Reverse Geocoding)",
            description = "Chuyển đổi tọa độ (latitude, longitude) sang tên địa điểm/địa chỉ hiển thị trên khung chia sẻ vị trí Chat."
    )
    public ResponseEntity<ApiResponse<ChatPlaceDto>> address(
            @PathVariable long id,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        if (!chats.detail(id, user).canSend()) throw new AppException(ErrorCode.CHAT_UNAVAILABLE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(addresses.address(latitude, longitude)));
    }
}
