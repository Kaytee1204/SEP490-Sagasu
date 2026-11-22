package com.example.base.service.report;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportReviewCalendarTest {
  private final ReportReviewCalendar calendar = new ReportReviewCalendar(
      "Asia/Ho_Chi_Minh", "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
      "08:00-12:00,13:00-17:00", "2026-11-23");

  @Test
  void urgentDeadlineSkipsLunch() {
    Instant submitted = Instant.parse("2026-11-20T04:00:00Z"); // Friday at 11:00
    assertThat(calendar.responseDue(submitted, 0)).isEqualTo(Instant.parse("2026-11-20T07:00:00Z"));
    assertThat(calendar.currentOrNext(Instant.parse("2026-11-20T05:30:00Z")).start())
        .isEqualTo(Instant.parse("2026-11-20T06:00:00Z"));
  }

  @Test
  void FridayAfterHoursStartsTuesdayWhenMondayIsAHoliday() {
    Instant submitted = Instant.parse("2026-11-20T11:00:00Z");
    assertThat(calendar.currentOrNext(submitted).start()).isEqualTo(Instant.parse("2026-11-24T01:00:00Z"));
    assertThat(calendar.responseDue(submitted, 1)).isEqualTo(Instant.parse("2026-11-24T10:00:00Z"));
    assertThat(calendar.responseDue(submitted, 2)).isEqualTo(Instant.parse("2026-11-25T10:00:00Z"));
    var nextShift = calendar.currentOrNext(submitted);
    assertThat(calendar.carriedOver(submitted, nextShift)).isFalse();
    assertThat(calendar.carriedOver(Instant.parse("2026-11-20T04:00:00Z"), nextShift)).isTrue();
  }
}
