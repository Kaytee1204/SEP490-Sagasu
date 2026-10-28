package com.example.base.service.post;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import java.time.OffsetDateTime;

/** A user's proposed handover time, not an appointment confirmed by Approver. */
final class PostHandoverPolicy {
    private PostHandoverPolicy() {}

    static void validateRequestedTime(String postType, String custodyMode, OffsetDateTime requestedAt) {
        boolean officeHandover = "FOUND".equals(postType) && "OFFICE".equals(custodyMode);
        if (officeHandover && requestedAt == null) {
            throw new AppException(ErrorCode.POST_HANDOVER_TIME_REQUIRED);
        }
        if (requestedAt != null && (!officeHandover || !requestedAt.isAfter(OffsetDateTime.now()))) {
            throw new AppException(ErrorCode.POST_HANDOVER_TIME_INVALID);
        }
    }
}
