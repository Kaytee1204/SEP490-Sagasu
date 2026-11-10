package com.example.base.controller.report;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/report-review")
@RequiredArgsConstructor
public class ReportReviewController {

  private final ReportReviewService reviews;
  private final ReportInquiryService inquiries;
  private final ReportChatService chat;

  @GetMapping
  public ApiResponse<?> list(
    @RequestParam(name = "page", defaultValue = "1") int page,
    @RequestParam(name = "status", defaultValue = "ALL") String status,
    @RequestParam(name = "kind", defaultValue = "ALL") String kind,
    @RequestParam(name = "target", defaultValue = "ALL") String target,
    @RequestParam(name = "q", defaultValue = "") String q,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(reviews.list(page, status, kind, target, q, user));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(
    @PathVariable("id") long id,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(reviews.detail(id, user));
  }

  @PostMapping("/{id}/actions")
  public ApiResponse<?> action(
    @PathVariable("id") long id,
    @Valid @RequestBody ReportReviewService.Action request,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(reviews.act(id, request, user));
  }

  @PostMapping("/{id}/inquiries")
  public ApiResponse<?> ask(
    @PathVariable("id") long id,
    @Valid @RequestBody ReportInquiryService.Ask request,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(inquiries.ask(id, request, user));
  }

  @GetMapping("/inquiries/mine")
  public ApiResponse<?> mine(@AuthenticationPrincipal CustomUserDetails user) {
    return ApiResponse.ok(inquiries.mine(user));
  }

  @GetMapping("/results/mine")
  public ApiResponse<?> results(
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(inquiries.results(user));
  }

  @PostMapping(
    value = "/inquiries/{id}/reply",
    consumes = "multipart/form-data"
  )
  public ApiResponse<?> reply(
    @PathVariable("id") long id,
    @Valid @RequestPart("data") ReportInquiryService.Reply request,
    @RequestPart(value = "images", required = false) List<MultipartFile> files,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(inquiries.reply(id, request, files, user));
  }

  @GetMapping("/{id}/chat")
  public ApiResponse<?> chat(
    @PathVariable("id") long id,
    @RequestParam(name = "before", required = false) Long before,
    @AuthenticationPrincipal CustomUserDetails user
  ) {
    return ApiResponse.ok(chat.read(id, before, user));
  }
}
