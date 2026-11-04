package com.example.base.controller.office;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.service.office.OfficeInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/office-invitations") @RequiredArgsConstructor
public class OfficeInvitationController {
    private final OfficeInvitationService service;
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<View>> preview(@Valid @RequestBody Preview request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.preview(request.token())));
    }
    @PostMapping("/respond")
    public ResponseEntity<ApiResponse<View>> respond(@Valid @RequestBody Response request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.respond(request)));
    }
}
