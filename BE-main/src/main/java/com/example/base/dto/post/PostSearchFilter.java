package com.example.base.dto.post;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Search public text only; dates represent whole days in Vietnam. */
public record PostSearchFilter(String keyword, Long categoryId, String postType, String location,
                               OffsetDateTime fromTime, OffsetDateTime untilTime) {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    public static PostSearchFilter empty() {
        return new PostSearchFilter("", null, "", "", null, null);
    }

    public static PostSearchFilter parse(String keyword, String categoryId, String postType,
            String location, String fromDate, String toDate, String timeRange) {
        PostSearchFilter filter = parse(keyword, categoryId, postType, location, fromDate, toDate);
        String range = clean(timeRange).toUpperCase(Locale.ROOT);
        if (range.isEmpty() || range.equals("CUSTOM")) return filter;
        if ((!range.equals("24H") && !range.equals("7D"))
                || filter.fromTime() != null || filter.untilTime() != null) {
            throw new AppException(ErrorCode.POST_SEARCH_TIME_RANGE_INVALID);
        }
        OffsetDateTime now = OffsetDateTime.now(VIETNAM);
        return new PostSearchFilter(filter.keyword(), filter.categoryId(), filter.postType(), filter.location(),
                range.equals("24H") ? now.minusHours(24) : now.minusDays(7), now);
    }

    public static PostSearchFilter parse(String keyword, String categoryId, String postType,
                                         String location, String fromDate, String toDate) {
        String query = clean(keyword);
        String place = clean(location);
        String type = clean(postType).toUpperCase(Locale.ROOT);
        if (query.length() > 200 || place.length() > 1000
                || !(type.isEmpty() || type.equals("LOST") || type.equals("FOUND"))) {
            throw new AppException(ErrorCode.POST_SEARCH_FILTER_INVALID);
        }
        Long category = null;
        if (!clean(categoryId).isEmpty()) {
            try {
                category = Long.valueOf(clean(categoryId));
                if (category <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                throw new AppException(ErrorCode.POST_SEARCH_FILTER_INVALID);
            }
        }
        try {
            LocalDate from = clean(fromDate).isEmpty() ? null : LocalDate.parse(clean(fromDate));
            LocalDate to = clean(toDate).isEmpty() ? null : LocalDate.parse(clean(toDate));
            if ((from != null && (from.getYear() < 1 || from.getYear() > 9999))
                    || (to != null && (to.getYear() < 1 || to.getYear() > 9999))
                    || (from != null && to != null && from.isAfter(to))) {
                throw new AppException(ErrorCode.POST_SEARCH_DATE_INVALID);
            }
            return new PostSearchFilter(query, category, type, place,
                    from == null ? null : from.atStartOfDay(VIETNAM).toOffsetDateTime(),
                    to == null ? null : to.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
        } catch (DateTimeParseException ex) {
            throw new AppException(ErrorCode.POST_SEARCH_DATE_INVALID);
        }
    }

    public boolean isEmpty() {
        return keyword.isEmpty() && categoryId == null && postType.isEmpty() && location.isEmpty()
                && fromTime == null && untilTime == null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
