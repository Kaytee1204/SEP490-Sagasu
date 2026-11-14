package com.example.base.controller.chat;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.ChatDto;
import com.example.base.dto.chat.ChatPageDto;
import com.example.base.dto.chat.ChatMessageDto;
import com.example.base.dto.chat.SendChatRequest;
import com.example.base.dto.chat.ReadChatRequest;
import com.example.base.dto.chat.ChatLocationRequest;
import com.example.base.service.chat.ChatReadService;
import com.example.base.repository.ChatQueryRepository;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.chat.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(
        name = "07. Chat (P2P) - Trò chuyện & Chia sẻ vị trí",
        description = "Mở phòng chat P2P, Lịch sử tin nhắn, Gửi tin nhắn văn bản/hình ảnh/tài liệu, Chia sẻ vị trí bản đồ và Đánh dấu đã đọc"
)
public class ChatController {
    private final ChatService service;
    private final ChatMessageService messages;
    private final ChatReadService reads;
    private final ChatQueryRepository queries;
    private final ChatAccessPolicy policy;

    @PostMapping("/posts/{postId}/chat")
    @Operation(
            summary = "[7.1] Mở hoặc lấy phòng trò chuyện P2P theo Bài đăng (postId)",
            description = "Khởi tạo hoặc trả về cuộc hội thoại P2P giữa Người nhặt và Người nhận sau khi Claim được chấp nhận."
    )
    public ResponseEntity<ApiResponse<ChatDto>> open(
            @PathVariable long postId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.open(postId, user)));
    }

    @GetMapping("/chats")
    @Operation(
            summary = "[7.2] Danh sách các cuộc hội thoại P2P của tôi (Cursor Pagination)",
            description = "Trả về danh sách phòng chat kèm tin nhắn cuối cùng, số tin chưa đọc và trạng thái bài đăng."
    )
    public ResponseEntity<ApiResponse<ChatPageDto>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.list(cursor, size, user)));
    }

    @GetMapping("/chats/{id}")
    @Operation(
            summary = "[7.3] Xem thông tin chi tiết một cuộc hội thoại theo ID",
            description = "Trả về metadata cuộc hội thoại, thành viên tham gia, trạng thái Claim và quyền gửi tin nhắn."
    )
    public ResponseEntity<ApiResponse<ChatDto>> detail(
            @PathVariable long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id, user)));
    }

    @GetMapping("/chats/{id}/messages")
    @Operation(
            summary = "[7.4] Lấy lịch sử tin nhắn trong cuộc hội thoại (theo sequence_no)",
            description = "Hỗ trợ tải tin nhắn cũ hơn (beforeSequence) hoặc mới hơn (afterSequence)."
    )
    public ResponseEntity<ApiResponse<ChatMessageDto.Page>> history(
            @PathVariable long id,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(messages.history(id, beforeSequence, afterSequence, limit, user)));
    }

    @PostMapping(value = "/chats/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "[7.5] Gửi tin nhắn văn bản (TEXT) vào cuộc hội thoại",
            description = "Gửi tin nhắn chữ kèm clientMessageId (UUID chống gửi trùng) và phát sự kiện thời gian thực qua WebSocket STOMP."
    )
    public ResponseEntity<ApiResponse<ChatMessageDto>> send(
            @PathVariable long id,
            @Valid @RequestBody SendChatRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(messages.send(id, request, user)));
    }

    @PostMapping("/chats/{id}/locations")
    @Operation(
            summary = "[7.6] Gửi tin nhắn chia sẻ vị trí hẹn gặp trên bản đồ (LOCATION)",
            description = "Gửi tọa độ latitude, longitude và nhãn địa điểm hẹn gặp bàn giao đồ."
    )
    public ResponseEntity<ApiResponse<ChatMessageDto>> location(
            @PathVariable long id,
            @Valid @RequestBody ChatLocationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(messages.sendLocation(id, request, user)));
    }

    @PostMapping("/chats/{id}/read")
    @Operation(
            summary = "[7.7] Đánh dấu đã đọc tin nhắn trong cuộc hội thoại tới số thứ tự lastReadSequence",
            description = "Cập nhật last_read_sequence của người nhận và bắn sự kiện READ_RECEIPT về phía người gửi."
    )
    public ResponseEntity<ApiResponse<Long>> read(
            @PathVariable long id,
            @Valid @RequestBody ReadChatRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(reads.read(id, request.lastReadSequence(), user)));
    }

    @GetMapping("/chats/{id}/read-receipts")
    @Operation(
            summary = "[7.8] Truy vấn trạng thái đã đọc (Read Receipts) của các tin nhắn trong phòng chat",
            description = "Trả về mốc đọc của đối phương trong khoảng afterSequence đến throughSequence."
    )
    public ResponseEntity<ApiResponse<ChatMessageDto.Receipts>> receipts(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam long throughSequence,
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(messages.receipts(id, afterSequence, throughSequence, limit, user)));
    }

    @GetMapping("/chats/unread-count")
    @Operation(
            summary = "[7.9] Đếm tổng số cuộc hội thoại / tin nhắn chưa đọc của tôi",
            description = "Hiển thị badge số lượng tin nhắn chưa đọc trên thanh điều hướng."
    )
    public ResponseEntity<ApiResponse<Long>> unread(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(queries.unread(policy.actor(user).getId())));
    }
}
