package com.example.base.service.post;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import java.time.OffsetDateTime;

/** Custody changes use the separate handover action; appointments are currently disabled. */
final class PostHandoverPolicy {
    private PostHandoverPolicy() {}

    static void validateCreation(String custodyMode, OffsetDateTime requestedAt) {
        requireNoAppointment(requestedAt);
        if (custodyMode != null && !"P2P".equals(custodyMode)) {
            throw new AppException(ErrorCode.POST_CUSTODY_INVALID);
        }
    }

    static void validateEdit(String currentCustody, String requestedCustody, OffsetDateTime requestedAt) {
        requireNoAppointment(requestedAt);
        if (requestedCustody != null && !currentCustody.equals(requestedCustody)) {
            throw new AppException(ErrorCode.POST_CUSTODY_CHANGE_REQUIRES_HANDOVER);
        }
    }

    private static void requireNoAppointment(OffsetDateTime requestedAt) {
        if (requestedAt != null) throw new AppException(ErrorCode.POST_HANDOVER_TIME_DISABLED);
    }
}
