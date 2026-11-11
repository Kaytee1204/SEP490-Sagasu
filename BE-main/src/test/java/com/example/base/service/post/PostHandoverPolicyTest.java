package com.example.base.service.post;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostHandoverPolicyTest {

    @Test
    void validateCreation_whenCustodyNullAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateCreation(null, null));
    }

    @Test
    void validateCreation_whenP2pAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateCreation("P2P", null));
    }

    @Test
    void validateCreation_whenOffice_throwsPostCustodyInvalid() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateCreation("OFFICE", null));
        assertEquals(ErrorCode.POST_CUSTODY_INVALID, ex.getErrorCode());
    }

    @Test
    void validateCreation_whenAppointmentProvided_throwsPostHandoverTimeDisabled() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateCreation("P2P", OffsetDateTime.now()));
        assertEquals(ErrorCode.POST_HANDOVER_TIME_DISABLED, ex.getErrorCode());
    }

    @Test
    void validateEdit_whenChangingCustodyToOffice_throwsPostCustodyChangeRequiresHandover() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateEdit("P2P", "OFFICE", null));
        assertEquals(ErrorCode.POST_CUSTODY_CHANGE_REQUIRES_HANDOVER, ex.getErrorCode());
    }

    @Test
    void validateEdit_whenRequestedCustodyNull_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateEdit("P2P", null, null));
    }

    @Test
    void validateEdit_whenSameCustodyAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateEdit("P2P", "P2P", null));
    }

    @Test
    void validateEdit_whenAppointmentProvided_throwsPostHandoverTimeDisabled() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateEdit("P2P", "P2P", OffsetDateTime.now()));
        assertEquals(ErrorCode.POST_HANDOVER_TIME_DISABLED, ex.getErrorCode());
    }
}

