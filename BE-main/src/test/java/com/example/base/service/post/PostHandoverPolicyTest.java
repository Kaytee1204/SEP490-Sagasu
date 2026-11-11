package com.example.base.service.post;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostHandoverPolicyTest {

    // UTC008 - validateCreation: custodyMode=null and requestedAt=null succeeds
    @Test
    void validateCreation_whenCustodyNullAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateCreation(null, null));
    }

    // UTC009 - validateCreation: custodyMode="P2P" and requestedAt=null succeeds
    @Test
    void validateCreation_whenP2pAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateCreation("P2P", null));
    }

    // UTC010 - validateCreation: custodyMode="OFFICE" throws POST_CUSTODY_INVALID
    @Test
    void validateCreation_whenOffice_throwsPostCustodyInvalid() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateCreation("OFFICE", null));
        assertEquals(ErrorCode.POST_CUSTODY_INVALID, ex.getErrorCode());
    }

    // UTC011 - validateCreation: handover appointment timestamp throws POST_HANDOVER_TIME_DISABLED
    @Test
    void validateCreation_whenAppointmentProvided_throwsPostHandoverTimeDisabled() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateCreation("P2P", OffsetDateTime.now()));
        assertEquals(ErrorCode.POST_HANDOVER_TIME_DISABLED, ex.getErrorCode());
    }

    // UTC012 - validateEdit: changing custody from P2P to OFFICE throws POST_CUSTODY_CHANGE_REQUIRES_HANDOVER
    @Test
    void validateEdit_whenChangingCustodyToOffice_throwsPostCustodyChangeRequiresHandover() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateEdit("P2P", "OFFICE", null));
        assertEquals(ErrorCode.POST_CUSTODY_CHANGE_REQUIRES_HANDOVER, ex.getErrorCode());
    }

    // UTC013 - validateEdit: requestedCustody=null succeeds
    @Test
    void validateEdit_whenRequestedCustodyNull_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateEdit("P2P", null, null));
    }

    // Supplementary (PostHandoverPolicy.validateEdit): keeping same custody P2P without appointment succeeds
    @Test
    void validateEdit_whenSameCustodyAndNoAppointment_succeeds() {
        assertDoesNotThrow(() -> PostHandoverPolicy.validateEdit("P2P", "P2P", null));
    }

    // Supplementary (PostHandoverPolicy.validateEdit): providing handover appointment throws POST_HANDOVER_TIME_DISABLED
    @Test
    void validateEdit_whenAppointmentProvided_throwsPostHandoverTimeDisabled() {
        AppException ex = assertThrows(AppException.class,
                () -> PostHandoverPolicy.validateEdit("P2P", "P2P", OffsetDateTime.now()));
        assertEquals(ErrorCode.POST_HANDOVER_TIME_DISABLED, ex.getErrorCode());
    }
}

