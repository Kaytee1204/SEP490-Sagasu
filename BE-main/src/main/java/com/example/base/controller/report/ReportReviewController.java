package com.example.base.controller.report;

import com.example.base.dto.ApiResponse;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/report-review")
@RequiredArgsConstructor
@Tag(
        name = "13. Reports & Dispute Resolution - Báo cáo & Điều tra giải quyết",
        description = "Gửi báo cáo vi phạm, Xem báo cáo của tôi, Điều tra của Manager (Chụp Snapshot, Tạm dừng giao nhận Hold, Hỏi đáp Inquiry, Kết luận xử lý)"
)
public class ReportReviewController {

    private final ReportReviewService reviews;
    private final ReportInquiryService inquiries;
    private final ReportChatService chat;
    private final ReportCaseService cases;
    private final ReportWorkbenchService workbench;

    @GetMapping("/work-queue")
    @Operation(summary="Hàng đợi theo đối tượng",description="Nhóm báo cáo cùng đối tượng; lọc NEW, MINE, WAITING, UPDATED, DONE, ALL. Không tự gộp kết luận.")
    public ApiResponse<?> workQueue(@RequestParam(name="page",defaultValue="1") int page,@RequestParam(name="stage",defaultValue="NEW") String stage,@RequestParam(name="q",defaultValue="") String q,@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(workbench.queue(page,stage,q,user));
    }
    @GetMapping("/target-reports")
    @Operation(summary="Đối chiếu báo cáo cùng đối tượng",description="Phân trang 20 báo cáo, giữ nguồn bằng chứng và người gửi riêng. key: P:id, C:id, M:id, U:id.")
    public ApiResponse<?> targetReports(@RequestParam(name="key") String key,@RequestParam(name="page",defaultValue="1") int page,@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(workbench.group(key,page,user));
    }
    @PostMapping("/batch-actions")
    @Operation(summary="Nhận xử lý hoặc kết luận các báo cáo đã chọn",description="TAKE hoặc CONCLUDE; tối đa 20 báo cáo cùng đối tượng; kiểm tra phiên bản, quyền và giao dịch toàn bộ. CONCLUDE chỉ dành cho GENERAL. Biện pháp áp dụng một lần; quyết định và thông báo riêng.")
    public ApiResponse<?> conclusions(@Valid @RequestBody ReportWorkbenchService.Batch request,@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(workbench.conclude(request,user));
    }

    @GetMapping("/{id}/case")
    public ApiResponse<?> caseDetail(@PathVariable("id") long id, @RequestParam(name="page",defaultValue="1") int page, @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(cases.detail(id,page,user));
    }
    @PostMapping("/{id}/case")
    public ApiResponse<?> linkCase(@PathVariable("id") long id, @Valid @RequestBody ReportCaseService.Link request, @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(cases.link(id,request,user));
    }
    @PostMapping("/{id}/case/unlink")
    public ApiResponse<?> unlinkCase(@PathVariable("id") long id, @Valid @RequestBody ReportCaseService.Unlink request, @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(cases.unlink(id,request,user));
    }

    @GetMapping
    @Operation(
            summary = "[13.5] Hàng đợi danh sách báo cáo vi phạm & tranh chấp dành cho Quản lý (MANAGER)",
            description = "Lọc theo status (ALL, OPEN, IN_REVIEW, RESOLVED, REJECTED), kind (GENERAL, HANDOVER_ISSUE), target và từ khóa q."
    )
    public ApiResponse<?> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "status", defaultValue = "ALL") String status,
            @RequestParam(name = "kind", defaultValue = "ALL") String kind,
            @RequestParam(name = "target", defaultValue = "ALL") String target,
            @RequestParam(name = "q", defaultValue = "") String q,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(reviews.list(page, status, kind, target, q, user));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "[13.6] Xem hồ sơ điều tra chi tiết của một báo cáo (bao gồm Snapshot, Bằng chứng, Lịch sử & Quyết định)",
            description = "Dành cho MANAGER đối chiếu ngữ cảnh chụp lúc báo cáo (report_snapshot) và trạng thái hiện tại."
    )
    public ApiResponse<?> detail(
            @PathVariable("id") long id,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(reviews.detail(id, user));
    }

    @PostMapping("/{id}/actions")
    @Operation(
            summary = "[13.7] Thực hiện thao tác xử lý báo cáo (TAKE / HOLD / RELEASE / CONCLUDE) dành cho MANAGER",
            description = "Hỗ trợ: Tiếp nhận xử lý (TAKE), Tạm dừng giao nhận bài đăng (HOLD), Gỡ tạm dừng (RELEASE), và Kết luận vụ việc (CONCLUDE kèm outcome VIOLATION / SETTLED / UNSUBSTANTIATED / HANDOVER_COMPLETED / HANDOVER_CONTINUE / CLAIM_REJECTED)."
    )
    public ApiResponse<?> action(
            @PathVariable("id") long id,
            @Valid @RequestBody ReportReviewService.Action request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(reviews.act(id, request, user));
    }

    @PostMapping("/{id}/inquiries")
    @Operation(
            summary = "[13.8] Manager gửi yêu cầu giải trình / bổ sung thông tin (Inquiry) tới các bên liên quan",
            description = "Tạo câu hỏi điều tra (report_inquiry) và gửi thông báo cho người dùng liên quan."
    )
    public ApiResponse<?> ask(
            @PathVariable("id") long id,
            @Valid @RequestBody ReportInquiryService.Ask request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(inquiries.ask(id, request, user));
    }

    @GetMapping("/inquiries/mine")
    @Operation(
            summary = "[13.9] Danh sách các yêu cầu giải trình (Inquiries) từ Manager gửi cho tôi",
            description = "Dành cho người dùng xem các câu hỏi xác minh từ Manager cần phản hồi."
    )
    public ApiResponse<?> mine(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(inquiries.mine(user));
    }

    @PostMapping(value = "/inquiries/{id}/reply", consumes = "multipart/form-data")
    @Operation(
            summary = "[13.10] Người dùng gửi phản hồi giải trình kèm ảnh bằng chứng cho câu hỏi của Manager",
            description = "Lưu nội dung trả lời vào report_inquiry và đính kèm tệp bằng chứng vào report_reply_evidence."
    )
    public ApiResponse<?> reply(
            @PathVariable("id") long id,
            @Valid @RequestPart("data") ReportInquiryService.Reply request,
            @RequestPart(value = "images", required = false) List<MultipartFile> files,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(inquiries.reply(id, request, files, user));
    }

    @GetMapping("/results/mine")
    @Operation(
            summary = "[13.11] Danh sách kết quả xử lý các báo cáo có liên quan tới tôi",
            description = "Hiển thị kết luận chính thức của Manager cho người báo cáo và các bên liên quan."
    )
    public ApiResponse<?> results(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(inquiries.results(user));
    }

    @GetMapping("/{id}/chat")
    @Operation(
            summary = "[13.12] Manager xem ngữ cảnh lịch sử tin nhắn Chat liên quan đến báo cáo",
            description = "Đọc lịch sử cuộc hội thoại P2P được đính kèm trong hồ sơ điều tra báo cáo."
    )
    public ApiResponse<?> chat(
            @PathVariable("id") long id,
            @RequestParam(name = "before", required = false) Long before,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ApiResponse.ok(chat.read(id, before, user));
    }
}
