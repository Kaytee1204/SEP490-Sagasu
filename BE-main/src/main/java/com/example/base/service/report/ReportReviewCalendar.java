package com.example.base.service.report;

import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ReportReviewCalendar {
  private final ZoneId zone;
  private final Set<DayOfWeek> workDays;
  private final List<Session> sessions;
  private final Set<LocalDate> holidays;

  ReportReviewCalendar(
      @Value("${report-review.calendar.zone:Asia/Ho_Chi_Minh}") String zone,
      @Value("${report-review.calendar.work-days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String days,
      @Value("${report-review.calendar.sessions:08:00-12:00,13:00-17:00}") String hours,
      @Value("${report-review.calendar.holidays:}") String holidays) {
    this.zone = ZoneId.of(zone);
    this.workDays = Arrays.stream(days.split(",")).map(String::strip).map(DayOfWeek::valueOf)
        .collect(Collectors.toUnmodifiableSet());
    this.sessions = Arrays.stream(hours.split(",")).map(Session::parse)
        .sorted(java.util.Comparator.comparing(Session::start)).toList();
    this.holidays = Arrays.stream(holidays.split(",")).map(String::strip).filter(date -> !date.isEmpty())
        .map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
    if (workDays.isEmpty() || sessions.isEmpty()) throw new IllegalArgumentException("Report review calendar needs working hours");
    for (int i = 1; i < sessions.size(); i++) {
      if (sessions.get(i - 1).end().isAfter(sessions.get(i).start()))
        throw new IllegalArgumentException("Report review work sessions overlap");
    }
  }

  Shift currentOrNext(Instant time) {
    ZonedDateTime local = time.atZone(zone);
    LocalDate day = local.toLocalDate();
    while (true) {
      if (workDays.contains(day.getDayOfWeek()) && !holidays.contains(day)) {
        for (Session session : sessions) {
          Instant end = day.atTime(session.end()).atZone(zone).toInstant();
          if (end.isAfter(time)) return new Shift(day.atTime(session.start()).atZone(zone).toInstant(), end);
        }
      }
      day = day.plusDays(1);
    }
  }

  Instant responseDue(Instant submittedAt, int priority) {
    Duration remaining = Duration.ofHours(priority == 0 ? 2 : priority == 1 ? 8 : 16);
    Instant cursor = submittedAt;
    while (true) {
      Shift shift = currentOrNext(cursor);
      Instant start = shift.start().isAfter(cursor) ? shift.start() : cursor;
      Duration available = Duration.between(start, shift.end());
      if (available.compareTo(remaining) >= 0) return start.plus(remaining);
      remaining = remaining.minus(available);
      cursor = shift.end();
    }
  }

  String zone() { return zone.getId(); }

  boolean carriedOver(Instant submittedAt, Shift currentShift) {
    LocalDate firstWorkDay = currentOrNext(submittedAt).start().atZone(zone).toLocalDate();
    return firstWorkDay.isBefore(currentShift.start().atZone(zone).toLocalDate());
  }

  record Shift(Instant start, Instant end) {}

  private record Session(LocalTime start, LocalTime end) {
    static Session parse(String value) {
      String[] ends = value.strip().split("-", -1);
      if (ends.length != 2) throw new IllegalArgumentException("Use HH:mm-HH:mm for each report review work session");
      LocalTime start = LocalTime.parse(ends[0].strip());
      LocalTime end = LocalTime.parse(ends[1].strip());
      if (!start.isBefore(end)) throw new IllegalArgumentException("Report review work session must end after it starts");
      return new Session(start, end);
    }
  }
}
