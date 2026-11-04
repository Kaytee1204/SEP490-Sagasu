package com.example.base.controller.chat;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.chat.ChatPlaceDto;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/chats/{id}/locations/address") @RequiredArgsConstructor
public class ChatLocationAddressController {
    private final ChatService chats;
    private final ChatLocationAddressService addresses;
    @GetMapping
    public ResponseEntity<ApiResponse<ChatPlaceDto>> address(@PathVariable long id, @RequestParam double latitude,
            @RequestParam double longitude, @AuthenticationPrincipal CustomUserDetails user) {
        if (!chats.detail(id, user).canSend()) throw new AppException(ErrorCode.CHAT_UNAVAILABLE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(addresses.address(latitude, longitude)));
    }
}
