package com.example.base.controller.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/chats") @RequiredArgsConstructor
public class ChatAttachmentController {
    private final ChatAttachmentService service;
    private final ChatProperties limits;
    @GetMapping("/limits")
    public ResponseEntity<ApiResponse<ChatProperties>> limits() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(limits));
    }
    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatMessageDto>> send(@PathVariable long id,
            @Valid @RequestPart("data") SendChatRequest request, @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.send(id, request, files, user)));
    }
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable long attachmentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        var file = service.download(id, attachmentId, user);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name() == null ? "attachment" : file.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.mime())).body(file.bytes());
    }
}
