package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportHoldGuard {

  private final JdbcTemplate jdbc;

  public boolean held(long post) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject(
        "select exists(select 1 from report_hold where post_id=? and released_at is null)",
        Boolean.class,
        post
      )
    );
  }

  public void requireAvailable(long post) {
    if (held(post)) throw bad(
      "Việc giao nhận đang được quản lý tạm dừng để đối chiếu báo cáo."
    );
  }
}
