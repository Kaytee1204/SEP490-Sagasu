package com.example.base.service.office;

import com.example.base.dto.office.InvitationDtos.Response;
import com.example.base.entity.*;
import com.example.base.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OfficeEmailInvitationTest {
    ClaimRepository claims; PostRepository posts; AppointmentRepository appointments;
    ClaimActivityRepository activities; AppointmentEmailService mailer; OfficeInvitationService service;
    Claim claim; Post post; Appointment appointment; String token; EntityManager em;
    @BeforeEach void setup() {
        claims=mock(ClaimRepository.class); posts=mock(PostRepository.class); appointments=mock(AppointmentRepository.class);
        activities=mock(ClaimActivityRepository.class); mailer=mock(AppointmentEmailService.class); em=mock(EntityManager.class);
        var tm=mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service=new OfficeInvitationService(claims,posts,appointments,activities,mailer,tm,em);
        var owner=new Account(); owner.setId(4L); owner.setEmail("owner@example.test");
        var staff=new Account(); staff.setId(3L);
        post=mock(Post.class); when(post.getId()).thenReturn(2L); when(post.getStatus()).thenReturn("RESERVED"); when(post.getTitle()).thenReturn("Wallet");
        claim=Claim.office(post,owner,staff,"COUNTER","Owner",owner.getEmail(),null,"Private evidence",null);
        ReflectionTestUtils.setField(claim,"id",7L); claim.review("APPROVED");
        appointment=Appointment.schedule(claim,staff,OffsetDateTime.now().plusDays(2),"Office A101",null);
        ReflectionTestUtils.setField(appointment,"id",9L);
        when(claims.findPostId(7L)).thenReturn(Optional.of(2L)); when(posts.findForClaimUpdate(2L)).thenReturn(Optional.of(post));
        when(claims.findForUpdate(7L)).thenReturn(Optional.of(claim));
        when(appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(7L,"SCHEDULED")).thenReturn(Optional.of(appointment));
        token=InvitationTokens.create();
        when(appointments.findByResponseTokenHash(InvitationTokens.hash(token))).thenReturn(Optional.of(appointment));
    }
    void sent() { appointment.invitationSent(InvitationTokens.hash(token),OffsetDateTime.now()); }
    @Test void previewDoesNotChangeClaimOrAppointment() {
        sent(); assertEquals("PENDING",service.preview(token).status()); assertEquals("APPROVED",claim.getStatus());
        verifyNoInteractions(activities); verify(post,never()).reopenAfterClaimCancellation();
    }
    @Test void invalidTokenCannotAccessInvitation() {
        assertThrows(RuntimeException.class,()->service.preview("bad"));
        assertThrows(RuntimeException.class,()->service.preview(InvitationTokens.create()));
    }
    @Test void confirmationIsSingleUse() {
        sent(); assertEquals("CONFIRMED",service.respond(new Response(token,"CONFIRM")).status());
        assertEquals("CONFIRMED",service.respond(new Response(token,"DECLINE")).status());
        assertEquals("APPROVED",claim.getStatus()); verify(activities,times(1)).save(any());
        verify(post,never()).reopenAfterClaimCancellation();
    }
    @Test void declineCancelsAppointmentAndReleasesItem() {
        sent(); assertEquals("DECLINED",service.respond(new Response(token,"DECLINE")).status());
        assertEquals("REJECTED",claim.getStatus()); assertEquals("CANCELLED",appointment.getStatus()); verify(post).reopenAfterClaimCancellation();
    }
    @Test void lateConfirmationExpiresRatherThanAccepts() {
        appointment.invitationSent(InvitationTokens.hash(token),OffsetDateTime.now().minusHours(13));
        assertEquals("EXPIRED",service.respond(new Response(token,"CONFIRM")).status()); assertEquals("REJECTED",claim.getStatus());
    }
    @Test void failedEmailDoesNotStartClockOrIssueToken() {
        assertFalse(service.send(7L)); assertNull(appointment.getResponseDeadline()); assertNull(appointment.getResponseTokenHash());
    }
    @Test void successfulEmailStoresOnlyHashAndStartsTwelveHours() {
        when(mailer.send(any(),anyString(),any())).thenReturn(true);
        assertTrue(service.send(7L)); assertEquals(64,appointment.getResponseTokenHash().length());
        assertEquals(appointment.getEmailSentAt().plusHours(12),appointment.getResponseDeadline());
    }
    @Test void successfulResendRotatesTokenWithoutExtendingDeadline() {
        sent(); var deadline=appointment.getResponseDeadline(); var old=appointment.getResponseTokenHash();
        when(mailer.send(any(),anyString(),any())).thenReturn(true); assertTrue(service.send(7L));
        assertEquals(deadline,appointment.getResponseDeadline()); assertNotEquals(old,appointment.getResponseTokenHash());
        assertThrows(RuntimeException.class,()->service.respond(new Response(token,"CONFIRM")));
    }
    @Test void failedResendPreservesWorkingToken() {
        sent(); var deadline=appointment.getResponseDeadline(); var old=appointment.getResponseTokenHash();
        assertFalse(service.send(7L)); assertEquals(old,appointment.getResponseTokenHash()); assertEquals(deadline,appointment.getResponseDeadline());
    }
    @Test void schedulerExpiresOnlyOnce() {
        appointment.invitationSent(InvitationTokens.hash(token),OffsetDateTime.now().minusHours(13));
        when(appointments.findExpiredOfficeClaims(any(),any())).thenReturn(List.of(7L));
        service.expireDue(); service.expireDue(); verify(activities,times(1)).save(any()); verify(post,times(1)).reopenAfterClaimCancellation();
    }
    @Test void confirmedInvitationsDoNotExpire() {
        appointment.invitationSent(InvitationTokens.hash(token),OffsetDateTime.now().minusHours(13)); appointment.confirmInvitation(OffsetDateTime.now().minusHours(2));
        when(appointments.findExpiredOfficeClaims(any(),any())).thenReturn(List.of(7L)); service.expireDue(); verifyNoInteractions(activities);
    }
    @Test void exactDeadlineCountsAsExpired() {
        var now=OffsetDateTime.now(); appointment.invitationSent(InvitationTokens.hash(token),now.minusHours(12));
        assertTrue(appointment.responseExpired(now)); assertFalse(appointment.responseExpired(now.minusNanos(1)));
    }
    @Test void rotatedTokenIsRecheckedAfterLock() {
        sent(); doAnswer(inv->{ ReflectionTestUtils.setField(appointment,"responseTokenHash",InvitationTokens.hash(InvitationTokens.create())); return null; })
            .when(em).refresh(eq(appointment),any(jakarta.persistence.LockModeType.class));
        assertThrows(RuntimeException.class,()->service.respond(new Response(token,"CONFIRM"))); assertEquals("PENDING",appointment.getResponseStatus());
    }
}
