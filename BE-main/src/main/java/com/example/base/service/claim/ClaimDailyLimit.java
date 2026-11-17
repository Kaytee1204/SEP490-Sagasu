package com.example.base.service.claim;

import com.example.base.entity.Account;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ClaimRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimDailyLimit {
    private final ClaimRepository claims;
    private final EntityManager em;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public ClaimDailyLimit(ClaimRepository claims, EntityManager em) {
        this(claims, em, Clock.systemUTC());
    }
    ClaimDailyLimit(ClaimRepository claims, EntityManager em, Clock clock) {
        this.claims = claims;
        this.em = em;
        this.clock = clock;
    }

    // Both submission paths hold the same account lock until the new dossier commits.
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailable(long accountId) {
        em.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE);
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var today = clock.instant().atZone(zone).toLocalDate();
        var start = today.atStartOfDay(zone).toOffsetDateTime();
        var end = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        if (claims.countByClaimant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(accountId, start, end) >= 2) {
            throw new AppException(ErrorCode.CLAIM_DAILY_LIMIT);
        }
    }
}
