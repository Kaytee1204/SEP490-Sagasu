package com.example.base.service.office;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration @EnableScheduling @RequiredArgsConstructor
public class OfficeInvitationScheduler {
    private final OfficeInvitationService invitations;
    @Scheduled(fixedDelayString="${office.appointments.expiry-check-ms:60000}", initialDelayString="${office.appointments.expiry-check-ms:60000}")
    public void expireInvitations() { invitations.expireDue(); }
}
