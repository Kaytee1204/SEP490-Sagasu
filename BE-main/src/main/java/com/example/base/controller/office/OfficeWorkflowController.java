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
    @GetMapping("/counter/people") public ApiResponse<List<CounterPerson>> counterPeople(@RequestParam(name="q",defaultValue="") String q,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.counterPeople(q,user));}
    @GetMapping("/counter/people/{id}/posts") public ApiResponse<List<CounterPost>> counterPosts(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.counterPosts(id,user));}
    @PostMapping("/counter/receive") public ApiResponse<Long> counterReceive(@Valid @RequestBody CounterIntake request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.receiveAtCounter(request,user));}
    private final OfficeReturnService returns;
    private final OfficeIntakeScheduleService intakeSchedules;
    private final OfficeInvitationService invitations;
    @PostMapping("/transfers/{id}/appointment-response") public ApiResponse<com.example.base.dto.office.InvitationDtos.View> respondIntake(@PathVariable("id") long id,@Valid @RequestBody AppointmentResponse request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(intakeSchedules.respondOnSite(id,request,user));}
    @PostMapping("/claims/{id}/appointment-response") public ApiResponse<com.example.base.dto.office.InvitationDtos.View> respondCollection(@PathVariable("id") long id,@Valid @RequestBody AppointmentResponse request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(invitations.respondOnSite(id,request,user));}
    @PostMapping("/transfers/{id}/schedule") public ApiResponse<String> schedule(@PathVariable("id") long id,@Valid @RequestBody IntakeSchedule request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(intakeSchedules.schedule(id,request,user));}
    @PostMapping("/transfers/{id}/appointment-email") public ApiResponse<String> resendIntake(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(intakeSchedules.send(id,user));}
    @GetMapping("/transfers")
    public ApiResponse<PageData<TransferView>> list(@RequestParam(name="page", defaultValue="1") int page,@RequestParam(name="status", defaultValue="PENDING") String status,@RequestParam(name="q", defaultValue="") String q,@RequestParam(name="mine", defaultValue="false") boolean mine,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.list(page,status,q,mine,user));}
    @GetMapping("/transfers/{id}") public ApiResponse<TransferView> transfer(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.detail(id,user));}
    @PostMapping("/transfers/{id}") public ApiResponse<TransferView> decide(@PathVariable("id") long id,@Valid @RequestBody TransferDecision request,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(transfers.decide(id,request,user));}
    @GetMapping("/claims/{id}") public ApiResponse<Workflow> detail(@PathVariable("id") long id,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.detail(id,user));}
    @PostMapping("/claims/{id}/reschedule") public ApiResponse<ReviewResult> reschedule(@PathVariable("id") long id,@Valid @RequestBody Reschedule r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.reschedule(id,r,user));}
    @PostMapping("/claims/{id}/cancel") public ApiResponse<Workflow> cancel(@PathVariable("id") long id,@Valid @RequestBody VersionedReason r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.cancel(id,r,user));}
    @PostMapping("/claims/{id}/verify") public ApiResponse<Workflow> verify(@PathVariable("id") long id,@Valid @RequestBody Verify r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.verify(id,r,user));}
    @PostMapping(value="/claims/{id}/handover",consumes="multipart/form-data") public ApiResponse<Workflow> record(@PathVariable("id") long id,@Valid @RequestPart("data") RecordHandover r,@RequestPart("images") List<MultipartFile> files,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.record(id,r,files,user));}
    @PostMapping("/claims/{id}/complete") public ApiResponse<Workflow> complete(@PathVariable("id") long id,@Valid @RequestBody Confirm r,@AuthenticationPrincipal CustomUserDetails user) {return ApiResponse.ok(returns.complete(id,r,user));}
}
