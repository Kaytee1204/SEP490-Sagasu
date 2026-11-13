package com.example.base.controller.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Tag(
        name = "07. Chat (P2P) - Trò chuyện & Chia sẻ vị trí",
        description = "Mở phòng chat P2P, Lịch sử tin nhắn, Gửi tin nhắn văn bản/hình ảnh/tài liệu, Chia sẻ vị trí bản đồ và Đánh dấu đã đọc"
)
public class ChatAttachmentController {
    private final ChatAttachmentService service;
    private final ChatProperties limits;

    @GetMapping("/limits")
    @Operation(
            summary = "[7.10] Xem cấu hình giới hạn dung lượng và định dạng tệp đính kèm trong Chat",
            description = "Trả về giới hạn số lượng file, kích thước tối đa mỗi file và các MIME type được phép gửi trong chat."
    )
    public ResponseEntity<ApiResponse<ChatProperties>> limits() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(limits));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "[7.11] Gửi tin nhắn kèm tệp đính kèm (Ảnh / PDF - ATTACHMENT hoặc MIXED)",
            description = "Tải tệp đính kèm lên Cloudflare R2 và tạo tin nhắn trong cuộc hội thoại."
    )
    public ResponseEntity<ApiResponse<ChatMessageDto>> send(
            @PathVariable long id,
            @Valid @RequestPart("data") SendChatRequest request,
            @RequestPart("files") List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.send(id, request, files, user)));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @Operation(
            summary = "[7.12] Tải xuống tệp đính kèm trong cuộc hội thoại",
            description = "Kiểm tra quyền thành viên cuộc hội thoại và tải tệp đính kèm về máy."
    )
    public ResponseEntity<byte[]> download(
            @PathVariable long id,
            @PathVariable long attachmentId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        var file = service.download(id, attachmentId, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name() == null ? "attachment" : file.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.mime())).body(file.bytes());
    }
}
