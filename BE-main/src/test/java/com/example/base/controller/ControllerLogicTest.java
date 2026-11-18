package com.example.base.controller;

import com.example.base.config.chat.ChatProperties;
import com.example.base.controller.chat.ChatAttachmentController;
import com.example.base.controller.post.PostController;
import com.example.base.controller.office.OfficeWorkflowController;
import com.example.base.controller.post.PostManagementController;
import com.example.base.dto.office.OfficeWorkflowDtos;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.security.CustomUserDetails;
import com.example.base.controller.chat.ChatLocationAddressController;
import com.example.base.dto.chat.ChatDto;
import com.example.base.dto.chat.ChatPlaceDto;
import com.example.base.service.chat.ChatAttachmentService;
import com.example.base.service.chat.ChatLocationAddressService;
import com.example.base.service.chat.ChatService;
import com.example.base.service.office.*;
import com.example.base.service.post.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Handlers that branch on their own (format parsing, permission pre-checks, response headers). */
@ExtendWith(MockitoExtension.class)
class ControllerLogicTest {

    @Mock ChatAttachmentService attachments;
    @Mock PostService posts;
    @Mock PostCreationService creation;
    @Mock PostPrivateIdentifiersService privateIdentifiers;
    @Mock SmartMatchService smartMatch;
    @Mock PostManagementService management;
    @Mock OfficeTransferService transfers;

    static final CustomUserDetails USER;

    static {
        Account a = new Account();
        a.setId(7L);
        a.setRole(AccountRole.USER);
        a.setStatus(AccountStatus.ACTIVE);
        USER = new CustomUserDetails(a);
    }

    private void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(code);
    }

    private PostController postController() {
        return new PostController(posts, creation, privateIdentifiers, smartMatch);
    }

    // ---------- ChatAttachmentController ----------

    @Test
    void utc001_download_setsAttachmentHeaderAndMime() {
        when(attachments.download(5L, 9L, USER)).thenReturn(new ChatAttachmentService.Download(new byte[]{1, 2}, "ảnh.png", "image/png"));

        ResponseEntity<byte[]> res = new ChatAttachmentController(attachments, mock(ChatProperties.class)).download(5L, 9L, USER);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains("UTF-8''");
        assertThat(res.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(res.getBody()).containsExactly(1, 2);
    }

    @Test
    void utc002_download_nullName_defaultsToAttachment() {
        when(attachments.download(5L, 9L, USER)).thenReturn(new ChatAttachmentService.Download(new byte[]{1}, null, "image/jpeg"));

        ResponseEntity<byte[]> res = new ChatAttachmentController(attachments, mock(ChatProperties.class)).download(5L, 9L, USER);

        assertThat(res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
    }

    @Test
    void utc003_limits_returnsConfiguredProperties() {
        ChatProperties limits = mock(ChatProperties.class);
        var res = new ChatAttachmentController(attachments, limits).limits();
        assertThat(res.getBody().getData()).isSameAs(limits);
    }

    // ---------- PostController / PostManagementController format checks ----------

    @Test
    void utc004_postList_nonNumericPage_paginationFormatInvalid() {
        assertCode(() -> postController().list("abc", "12", "", "", "", "", "", "", ""), ErrorCode.POST_PAGINATION_FORMAT_INVALID);
        assertCode(() -> postController().list("1", "1.5", "", "", "", "", "", "", ""), ErrorCode.POST_PAGINATION_FORMAT_INVALID);
        verifyNoInteractions(posts);
    }

    @Test
    void utc005_postDetail_nonNumericId_idFormatInvalid() {
        assertCode(() -> postController().detail("ten", USER), ErrorCode.POST_ID_FORMAT_INVALID);
    }

    @Test
    void utc006_postDetail_anonymousViewer_passesNullViewerId() {
        postController().detail("10", null);
        verify(posts).getDetailForViewer(10L, null);
    }

    @Test
    void utc007_myPosts_nonNumericSize_paginationFormatInvalid() {
        assertCode(() -> new PostManagementController(management).mine("1", "x", "ALL", "ALL", USER), ErrorCode.POST_PAGINATION_FORMAT_INVALID);
    }

    // ---------- OfficeWorkflowController.receive ----------

    @Test
    void utc008_receive_receivedDecision_delegatesWithImages() {
        var request = new OfficeWorkflowDtos.TransferDecision(3L, "RECEIVED", 2L, "Tốt", null, null);
        List<MultipartFile> images = List.of(mock(MultipartFile.class));

        officeWorkflow().receive(9L, request, images, USER);

        verify(transfers).decide(9L, request, USER, images);
    }

    @Test
    void utc009_receive_otherDecision_badRequest() {
        var request = new OfficeWorkflowDtos.TransferDecision(3L, "REJECTED", null, null, "Sai đồ", null);

        assertCode(() -> officeWorkflow().receive(9L, request, List.of(), USER), ErrorCode.BAD_REQUEST);
        verifyNoInteractions(transfers);
    }

    // ---------- ChatLocationAddressController ('Chia sẻ vị trí', UC-38) ----------

    @Test
    void utc010_address_sendableChat_callsGeocoder() {
        ChatService chats = mock(ChatService.class);
        ChatLocationAddressService addresses = mock(ChatLocationAddressService.class);
        ChatDto chat = mock(ChatDto.class);
        when(chat.canSend()).thenReturn(true);
        when(chats.detail(5L, USER)).thenReturn(chat);
        when(addresses.address(21.0, 105.8)).thenReturn(new ChatPlaceDto(21.0, 105.8, "FPT University"));

        var res = new ChatLocationAddressController(chats, addresses).address(5L, 21.0, 105.8, USER);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(addresses).address(21.0, 105.8);
    }

    @Test
    void utc011_address_readOnlyChat_chatUnavailableWithoutGeocoder() {
        ChatService chats = mock(ChatService.class);
        ChatLocationAddressService addresses = mock(ChatLocationAddressService.class);
        ChatDto chat = mock(ChatDto.class);
        when(chat.canSend()).thenReturn(false);
        when(chats.detail(5L, USER)).thenReturn(chat);

        assertCode(() -> new ChatLocationAddressController(chats, addresses).address(5L, 21.0, 105.8, USER),
                ErrorCode.CHAT_UNAVAILABLE);
        verifyNoInteractions(addresses);
    }

    private OfficeWorkflowController officeWorkflow() {
        return new OfficeWorkflowController(transfers, mock(OfficeReturnService.class),
                mock(OfficeIntakeScheduleService.class), mock(OfficeInvitationService.class));
    }
}
