package com.example.base.controller.chat;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.ChatDto;
import com.example.base.dto.chat.ChatPageDto;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ChatController {
    private final ChatService service;
    @GetMapping("/chats")
    public ResponseEntity<ApiResponse<ChatPageDto>> list(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.list(cursor, size, user)));
    }
    @PostMapping("/posts/{postId}/chat")
    public ResponseEntity<ApiResponse<ChatDto>> open(@PathVariable long postId, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.open(postId, user)));
    }
    @GetMapping("/chats/{id}")
    public ResponseEntity<ApiResponse<ChatDto>> detail(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.detail(id, user)));
    }
}
