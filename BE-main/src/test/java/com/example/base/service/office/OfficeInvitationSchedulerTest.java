package com.example.base.service.office;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OfficeInvitationSchedulerTest {

    @Mock
    OfficeInvitationService invitations;

    @InjectMocks
    OfficeInvitationScheduler scheduler;

    @Test
    void utc023_expireInvitations_delegatesToExpireDueOnce() {
        scheduler.expireInvitations();
        verify(invitations).expireDue();
    }
}

