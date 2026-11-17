package com.example.base.service.claim;

import com.example.base.entity.Account;
import com.example.base.exception.AppException;
import com.example.base.repository.ClaimRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimDailyLimitTest {
    @Test void allowsFirstAndSecondButRejectsThirdAcrossAllChannels() {
        var claims = mock(ClaimRepository.class);
        var em = mock(EntityManager.class);
        var policy = new ClaimDailyLimit(claims, em, Clock.fixed(Instant.parse("2026-11-17T16:59:59Z"), ZoneOffset.UTC));
        var start = OffsetDateTime.parse("2026-11-17T00:00:00+07:00");
        var end = start.plusDays(1);
        when(claims.countByClaimant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(7, start, end))
                .thenReturn(0L, 1L, 2L);
        assertDoesNotThrow(() -> policy.requireAvailable(7));
        assertDoesNotThrow(() -> policy.requireAvailable(7));
        assertThrows(AppException.class, () -> policy.requireAvailable(7));
        var order = inOrder(em, claims);
        order.verify(em).find(Account.class, 7L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(claims).countByClaimant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(7, start, end);
    }
    @Test void resetsAtVietnamMidnightRegardlessOfServerTimezone() {
        var claims = mock(ClaimRepository.class);
        var em = mock(EntityManager.class);
        var policy = new ClaimDailyLimit(claims, em, Clock.fixed(Instant.parse("2026-11-17T17:00:00Z"), ZoneOffset.ofHours(-8)));
        policy.requireAvailable(9);
        verify(claims).countByClaimant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(9,
                OffsetDateTime.parse("2026-11-18T00:00:00+07:00"), OffsetDateTime.parse("2026-11-19T00:00:00+07:00"));
    }
}
