package com.example.base.controller.office;

import com.example.base.dto.ApiResponse;
import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/office-workflow") @RequiredArgsConstructor
public class OfficeWorkflowController {
    private final OfficeTransferService transfers;
    private final OfficeReturnService returns;
    @GetMapping("/transfers")
    public ApiResponse<PageData<TransferView>> list(@RequestParam(name="page", defaultValue="1") int page,@RequestParam(name="status", defaultValue="PENDING") String status,@RequestParam(name="q", defaultValue="") String q,@RequestParam(name="mine", defaultValue="false") boolean mine,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.list(page,status,q,mine,user));}
    @GetMapping("/transfers/{id}") public ApiResponse<TransferView> transfer(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.detail(id,user));}
    @PostMapping("/transfers/{id}") public ApiResponse<TransferView> decide(@PathVariable("id") long id,@Valid @RequestBody TransferDecision request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.decide(id,request,user));}
    @GetMapping("/claims/{id}") public ApiResponse<Workflow> detail(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.detail(id,user));}
    @PostMapping("/claims/{id}/reschedule") public ApiResponse<ReviewResult> reschedule(@PathVariable("id") long id,@Valid @RequestBody Reschedule r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.reschedule(id,r,user));}
    @PostMapping("/claims/{id}/cancel") public ApiResponse<Workflow> cancel(@PathVariable("id") long id,@Valid @RequestBody VersionedReason r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.cancel(id,r,user));}
    @PostMapping("/claims/{id}/verify") public ApiResponse<Workflow> verify(@PathVariable("id") long id,@Valid @RequestBody Verify r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.verify(id,r,user));}
    @PostMapping(value="/claims/{id}/handover",consumes="multipart/form-data") public ApiResponse<Workflow> record(@PathVariable("id") long id,@Valid @RequestPart("data") RecordHandover r,@RequestPart("images") List<MultipartFile> files,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.record(id,r,files,user));}
    @PostMapping("/claims/{id}/confirm") public ApiResponse<Workflow> confirm(@PathVariable("id") long id,@Valid @RequestBody Confirm r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.confirm(id,r,user));}
    @PostMapping("/claims/{id}/issue") public ApiResponse<Workflow> issue(@PathVariable("id") long id,@Valid @RequestBody VersionedReason r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.issue(id,r,user));}
}
