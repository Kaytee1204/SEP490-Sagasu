package com.example.base.controller.office;
import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/office") @RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class OfficeManagementController {
    private final OfficeManagementService service;
    @GetMapping("/posts") public ApiResponse<PageData<PostView>> posts(@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="ALL") String type,
        @RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="ALL") String moderation,
        @RequestParam(defaultValue="ALL") String custody,@RequestParam(required=false) Long category,
        @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.posts(page,q,type,status,moderation,custody,category,user));
    }
    @GetMapping("/posts/{id}") public ApiResponse<PostView> detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.detail(id,user)); }
    @PostMapping("/posts/{id}/moderation") public ApiResponse<PostView> moderate(@PathVariable long id,@Valid @RequestBody Moderation request,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.moderate(id,request,user)); }
    @GetMapping("/stored-items") @PreAuthorize("hasAnyRole('APPROVER','MANAGER')")
    public ApiResponse<PageData<ReceiptView>> stored(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long locationId,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.stored(page,q,locationId,user)); }
    @GetMapping("/locations") @PreAuthorize("hasAnyRole('APPROVER','MANAGER')")
    public ApiResponse<List<LocationView>> locations(@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.locations(user)); }
    @PostMapping("/locations") public ApiResponse<LocationView> create(@Valid @RequestBody LocationInput r,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.saveLocation(null,r,user)); }
    @PutMapping("/locations/{id}") public ApiResponse<LocationView> update(@PathVariable long id,@Valid @RequestBody LocationInput r,@AuthenticationPrincipal CustomUserDetails user) { return ApiResponse.ok(service.saveLocation(id,r,user)); }
}
