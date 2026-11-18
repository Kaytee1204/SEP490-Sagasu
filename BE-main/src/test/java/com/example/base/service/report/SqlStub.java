package com.example.base.service.report;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/** Stubs JdbcTemplate.queryForList by SQL fragment; every call returns fresh mutable rows (services mutate them). */
final class SqlStub {
    private SqlStub() {}

    static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** Unmatched queries return an empty list. */
    static void defaults(JdbcTemplate jdbc) {
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> new ArrayList<>());
    }

    @SafeVarargs
    static void rows(JdbcTemplate jdbc, String sqlPart, Map<String, Object>... rows) {
        lenient().when(jdbc.queryForList(contains(sqlPart), any(Object[].class))).thenAnswer(inv -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (var r : rows) out.add(new LinkedHashMap<>(r));
            return out;
        });
    }
}
