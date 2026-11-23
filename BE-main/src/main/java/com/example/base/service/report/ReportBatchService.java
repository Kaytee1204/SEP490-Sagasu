package com.example.base.service.report;

import com.example.base.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;

@Service
@RequiredArgsConstructor
public class ReportBatchService {
  private final ReportReviewService reviews;
  public record Entry(@NotNull @Positive Long id, @NotNull @PositiveOrZero Long version) {}
  public record Request(@NotEmpty @Size(max=20) List<@Valid Entry> reports,
      @NotBlank String action, @NotBlank @Size(max=5000) String conclusion,
      @Size(max=5000) String basis, @AssertTrue boolean sameIncidentConfirmed) {}

  static boolean sameIssue(Map<String,Object> a, Map<String,Object> b) {
    return Objects.equals(a.get("kind"), b.get("kind"))
        && Objects.equals(a.get("reason_code"), b.get("reason_code"))
        && Arrays.asList("post_id","claim_id","message_id","reported_account_id").stream()
            .allMatch(key -> Objects.equals(a.get(key), b.get(key)));
  }

  @Transactional
  public void conclude(Request request, CustomUserDetails user) {
    boolean canBatch = Set.of("RESOLVE","DISMISS").contains(request.action())
        || ReportReviewService.VIOLATION_FINDINGS.contains(request.action());
    if (!canBatch || !request.sameIncidentConfirmed())
      throw bad("Chọn kết luận và xác nhận các báo cáo cùng sự việc.");
    var entries = request.reports().stream().sorted(Comparator.comparing(Entry::id)).toList();
    if (entries.stream().map(Entry::id).distinct().count()!=entries.size()) throw bad("Báo cáo bị chọn trùng.");
    Map<String,Object> first = null;
    // Lock in a stable order; any stale version or ownership failure rolls back the entire selection.
    for (var entry : entries) {
      var r = reviews.lock(entry.id());
      if (first == null) first=r;
      else if (!sameIssue(first,r)) throw bad("Chỉ kết luận chung báo cáo cùng đối tượng, loại và lý do. Các vấn đề khác cần xử lý riêng.");
      if (!Objects.equals(id(r,"version"),entry.version())) throw bad("Báo cáo đã thay đổi. Hãy tải lại trước khi kết luận.");
    }
    for (var entry : entries) reviews.act(entry.id(), new ReportReviewService.Action(
        entry.version(), request.action(), request.conclusion(), request.basis(), null, false), user);
  }
}
