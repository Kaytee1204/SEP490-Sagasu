package com.example.base.controller.office;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.service.office.OfficeIntakeScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/office-intake-invitations") @RequiredArgsConstructor
public class OfficeIntakeInvitationController {
    private final OfficeIntakeScheduleService service;
    @PostMapping("/preview") public ApiResponse<View> preview(@Valid @RequestBody Preview r){return ApiResponse.ok(service.preview(r.token()));}
    @PostMapping("/respond") public ApiResponse<View> respond(@Valid @RequestBody Response r){return ApiResponse.ok(service.respond(r));}
}
