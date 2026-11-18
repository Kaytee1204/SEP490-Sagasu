package com.example.base.service.account;

import com.example.base.dto.PageResponse;
import com.example.base.dto.account.AccountDto;
import com.example.base.dto.account.CreateAccountRequest;
import com.example.base.dto.account.ExcelImportResponse;
import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.dto.account.UpdateProfileRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.LoginAuditLog;
import com.example.base.entity.RefreshToken;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.AccountMapper;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.LoginAuditLogRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.example.base.security.CustomUserDetails;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One test per UTC of sheet "Account" (UTC001-UTC083). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private LoginAuditLogRepository loginAuditLogRepository;
    @Spy
    private AccountMapper accountMapper = new AccountMapper();
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService service;

    private Account admin;
    private Account manager;
    private Account user;
    private Account approver;

    @BeforeEach
    void setUp() {
        admin = account(1L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        manager = account(2L, "mgr@sagasu.vn", AccountRole.MANAGER, AccountStatus.ACTIVE);
        user = account(3L, "a@x.com", AccountRole.USER, AccountStatus.ACTIVE);
        approver = account(4L, "ap@x.com", AccountRole.APPROVER, AccountStatus.ACTIVE);
        for (Account a : List.of(admin, manager, user, approver)) {
            when(accountRepository.findById(a.getId())).thenReturn(Optional.of(a));
        }
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(accountRepository.searchAccounts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- getAccountsPage

    @Test
    void utc001_getAccountsPage_normalFiltersReturnPageAndStatusCounts() {
        List<Account> three = List.of(
                account(11L, "an1@gmail.com", AccountRole.USER, AccountStatus.ACTIVE),
                account(12L, "an2@gmail.com", AccountRole.USER, AccountStatus.ACTIVE),
                account(13L, "an3@gmail.com", AccountRole.USER, AccountStatus.ACTIVE));
        when(accountRepository.countByStatus(AccountStatus.ACTIVE)).thenReturn(10L);
        when(accountRepository.countByStatus(AccountStatus.SUSPENDED)).thenReturn(2L);
        when(accountRepository.countByStatus(AccountStatus.DELETED)).thenReturn(1L);
        when(accountRepository.searchAccounts(eq("an"), eq(AccountRole.USER), eq(AccountStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(three, PageRequest.of(0, 10), 3));

        PageResponse<AccountDto> res = service.getAccountsPage(1, 10, "an", AccountRole.USER, AccountStatus.ACTIVE, false);

        assertEquals(3, res.getContent().size());
        assertEquals(1, res.getPage());
        assertEquals(10, res.getSize());
        assertEquals(10L, res.getActiveCount());
        assertEquals(2L, res.getSuspendedCount());
        assertEquals(1L, res.getDeletedCount());
    }

    @Test
    void utc002_getAccountsPage_pageZeroClampedToFirstPage() {
        service.getAccountsPage(0, 10, null, null, null, false);
        assertEquals(0, capturedPageable().getPageNumber());
        assertEquals(10, capturedPageable().getPageSize());
    }

    @Test
    void utc003_getAccountsPage_negativePageAndZeroSizeClamped() {
        service.getAccountsPage(-5, 0, null, null, null, false);
        assertEquals(0, capturedPageable().getPageNumber());
        assertEquals(1, capturedPageable().getPageSize());
    }

    @Test
    void utc004_getAccountsPage_sizeCappedAt100() {
        service.getAccountsPage(1, 500, null, null, null, false);
        assertEquals(100, capturedPageable().getPageSize());
    }

    @Test
    void utc005_getAccountsPage_whitespaceKeywordBecomesNull() {
        service.getAccountsPage(1, 10, "   ", null, null, false);
        verify(accountRepository).searchAccounts(isNull(), isNull(), isNull(), any());
    }

    @Test
    void utc006_getAccountsPage_keywordTrimmed() {
        service.getAccountsPage(1, 10, "  nam  ", null, null, false);
        verify(accountRepository).searchAccounts(eq("nam"), isNull(), isNull(), any());
    }

    @Test
    void utc007_getAccountsPage_emptyResultReturnsZeroElements() {
        PageResponse<AccountDto> res = service.getAccountsPage(3, 10, null, null, null, false);
        assertTrue(res.getContent().isEmpty());
        assertEquals(0L, res.getTotalElements());
    }

    // ---------------------------------------------------------------- getAccountById / profile / audit logs

    @Test
    void utc008_getAccountById_existingReturnsDto() {
        assertEquals("a@x.com", service.getAccountById(3L).getEmail());
    }

    @Test
    void utc009_getAccountById_missingThrowsAccountNotFound() {
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.getAccountById(999L)));
    }

    @Test
    void utc010_getMyProfile_authenticatedReturnsActor() {
        authenticateAs(user);
        assertEquals(3L, service.getMyProfile().getId());
    }

    @Test
    void utc011_getMyProfile_unauthenticatedThrowsUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.getMyProfile()));
    }

    @Test
    void utc012_getMyProfile_actorDeletedFromDbThrowsUnauthorized() {
        authenticateAs(user);
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.getMyProfile()));
    }

    @Test
    void utc013_getMyAuditLogs_fiveLogsMappedInOrder() {
        authenticateAs(user);
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(3L)).thenReturn(auditLogs(5));
        List<Map<String, Object>> logs = service.getMyAuditLogs();
        assertEquals(5, logs.size());
        assertTrue(logs.get(0).containsKey("eventType"));
    }

    @Test
    void utc014_getMyAuditLogs_cappedAt30() {
        authenticateAs(user);
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(3L)).thenReturn(auditLogs(45));
        assertEquals(30, service.getMyAuditLogs().size());
    }

    @Test
    void utc015_getMyAuditLogs_noLogsReturnsEmpty() {
        authenticateAs(user);
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        assertTrue(service.getMyAuditLogs().isEmpty());
    }

    @Test
    void utc016_getMyAuditLogs_unauthenticatedThrowsUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.getMyAuditLogs()));
    }

    // ---------------------------------------------------------------- updateMyProfile

    @Test
    void utc017_updateMyProfile_validLocalPhoneUpdatedAndLogged() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPhone("0912345678");

        assertEquals("0912345678", service.updateMyProfile(req).getPhone());

        ArgumentCaptor<LoginAuditLog> log = ArgumentCaptor.forClass(LoginAuditLog.class);
        verify(loginAuditLogRepository).save(log.capture());
        assertEquals("PROFILE_UPDATED", log.getValue().getEventType());
        assertEquals(true, log.getValue().getDetails().get("phoneUpdated"));
        assertEquals(false, log.getValue().getDetails().get("passwordChanged"));
    }

    @Test
    void utc018_updateMyProfile_internationalPhoneAccepted() {
        assertEquals("+84912345678", updateMyPhone("+84912345678").getPhone());
    }

    @Test
    void utc019_updateMyProfile_shortPhoneThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("12345")));
    }

    @Test
    void utc020_updateMyProfile_nineDigitPhoneThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("091234567")));
    }

    @Test
    void utc021_updateMyProfile_blankPhoneClearsPhone() {
        assertEquals("", updateMyPhone("   ").getPhone());
    }

    @Test
    void utc022_updateMyProfile_validPasswordChangeReencodesAndClearsMustChange() {
        user.setMustChangePassword(true);
        authenticateAs(user);
        when(passwordEncoder.matches("Old@1234", "encoded-hash")).thenReturn(true);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("Old@1234");
        req.setNewPassword("New@1234");

        assertFalse(service.updateMyProfile(req).isMustChangePassword());
        assertEquals("enc:New@1234", user.getPasswordHash());
    }

    @Test
    void utc023_updateMyProfile_wrongCurrentPasswordThrowsInvalidCredentials() {
        authenticateAs(user);
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("New@1234");
        assertEquals(ErrorCode.INVALID_CREDENTIALS, code(() -> service.updateMyProfile(req)));
    }

    @Test
    void utc024_updateMyProfile_passwordWithoutSpecialCharThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("abc12345")));
    }

    @Test
    void utc025_updateMyProfile_sevenCharPasswordThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("Ab@1234")));
    }

    @Test
    void utc026_updateMyProfile_eightCharPasswordAccepted() {
        assertNotNull(updateMyPassword("Ab@12345"));
        assertEquals("enc:Ab@12345", user.getPasswordHash());
    }

    @Test
    void utc027_updateMyProfile_sixteenCharPasswordAccepted() {
        assertNotNull(updateMyPassword("Ab@1234567890123"));
        assertEquals("enc:Ab@1234567890123", user.getPasswordHash());
    }

    @Test
    void utc028_updateMyProfile_seventeenCharPasswordThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("Ab@12345678901234")));
    }

    @Test
    void utc029_updateMyProfile_userEscalatingToAdminThrowsForbidden() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.ADMIN);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateMyProfile(req)));
    }

    @Test
    void utc030_updateMyProfile_userKeepingSameRoleSucceeds() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.updateMyProfile(req).getRole());
    }

    @Test
    void utc031_updateMyProfile_adminMayChangeOwnRole() {
        authenticateAs(admin);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.MANAGER);
        assertEquals(AccountRole.MANAGER, service.updateMyProfile(req).getRole());
    }

    @Test
    void utc032_updateMyProfile_unauthenticatedThrowsUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.updateMyProfile(new UpdateProfileRequest())));
    }

    // ---------------------------------------------------------------- createAccount

    @Test
    void utc033_createAccount_adminCreatesNormalizedActiveAccount() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("New@GMAIL.com", " Nguyen A ");
        req.setPassword("Abc@1234");
        req.setRole(AccountRole.APPROVER);

        AccountDto dto = service.createAccount(req);

        assertEquals("new@gmail.com", dto.getEmail());
        assertEquals("Nguyen A", dto.getFullName());
        assertEquals(AccountRole.APPROVER, dto.getRole());
        assertEquals(AccountStatus.ACTIVE, dto.getStatus());
        assertFalse(dto.isMustChangePassword());
        assertEquals("Abc@1234", dto.getGeneratedPassword());
    }

    @Test
    void utc034_createAccount_nullPasswordGeneratesTenCharsAndForcesChange() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("rand@gmail.com", "Rand");
        req.setPassword(null);
        req.setMustChangePassword(null);

        AccountDto dto = service.createAccount(req);

        assertTrue(dto.isMustChangePassword());
        assertEquals(10, dto.getGeneratedPassword().length());
    }

    @Test
    void utc035_createAccount_nullRoleDefaultsToUser() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("rand@gmail.com", "Rand");
        req.setRole(null);
        assertEquals(AccountRole.USER, service.createAccount(req).getRole());
    }

    @Test
    void utc036_createAccount_managerAssigningNonUserRoleThrowsForbidden() {
        authenticateAs(manager);
        CreateAccountRequest req = createReq("ap@gmail.com", "Ap");
        req.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.createAccount(req)));
    }

    @Test
    void utc037_createAccount_managerCreatingUserSucceeds() {
        authenticateAs(manager);
        CreateAccountRequest req = createReq("u@gmail.com", "U");
        req.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.createAccount(req).getRole());
    }

    @Test
    void utc038_createAccount_malformedEmailThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(createReq("abc", "X"))));
    }

    @Test
    void utc039_createAccount_oneCharTldThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(createReq("a@b.c", "X"))));
    }

    @Test
    void utc040_createAccount_duplicateEmailThrowsEmailAlreadyExists() {
        authenticateAs(admin);
        when(accountRepository.existsByEmailIgnoreCase("dup@gmail.com")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, code(() -> service.createAccount(createReq("dup@gmail.com", "Dup"))));
    }

    @Test
    void utc041_createAccount_shortPasswordThrowsBadRequest() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("ok@gmail.com", "Ok");
        req.setPassword("short");
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(req)));
    }

    @Test
    void utc042_createAccount_customStatusValidUntilAndCreatorRecorded() {
        authenticateAs(admin);
        OffsetDateTime validUntil = OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        CreateAccountRequest req = createReq("susp@gmail.com", "Susp");
        req.setPassword("Abc@1234");
        req.setStatus(AccountStatus.SUSPENDED);
        req.setValidUntil(validUntil);

        AccountDto dto = service.createAccount(req);

        assertEquals(AccountStatus.SUSPENDED, dto.getStatus());
        assertEquals(validUntil, dto.getValidUntil());
        assertEquals(1L, dto.getCreatedById());
    }

    // ---------------------------------------------------------------- updateAccount

    @Test
    void utc043_updateAccount_adminUpdatesFullNameAndPhone() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("Tran B");
        req.setPhone("0987654321");
        AccountDto dto = service.updateAccount(3L, req);
        assertEquals("Tran B", dto.getFullName());
        assertEquals("0987654321", dto.getPhone());
    }

    @Test
    void utc044_updateAccount_missingTargetThrowsAccountNotFound() {
        authenticateAs(admin);
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.updateAccount(999L, new UpdateAccountRequest())));
    }

    @Test
    void utc045_updateAccount_managerTargetingAdminThrowsForbidden() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("X");
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateAccount(1L, req)));
    }

    @Test
    void utc046_updateAccount_managerMayUpdateApprover() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("X");
        assertEquals("X", service.updateAccount(4L, req).getFullName());
    }

    @Test
    void utc047_updateAccount_managerChangingRoleThrowsForbidden() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateAccount(3L, req)));
    }

    @Test
    void utc048_updateAccount_adminChangesUserRoleToApprover() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setRole(AccountRole.APPROVER);
        assertEquals(AccountRole.APPROVER, service.updateAccount(3L, req).getRole());
    }

    @Test
    void utc049_updateAccount_emailOwnedByAnotherThrowsEmailAlreadyExists() {
        authenticateAs(admin);
        when(accountRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, code(() -> service.updateAccount(3L, updateEmail("taken@x.com"))));
    }

    @Test
    void utc050_updateAccount_sameEmailDifferentCaseSkipsDuplicateCheck() {
        authenticateAs(admin);
        assertEquals("a@x.com", service.updateAccount(3L, updateEmail("A@X.COM")).getEmail());
        verify(accountRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void utc051_updateAccount_specialAdminEmailAccepted() {
        authenticateAs(admin);
        assertEquals("admin", service.updateAccount(3L, updateEmail("admin")).getEmail());
    }

    @Test
    void utc052_updateAccount_malformedEmailThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateAccount(3L, updateEmail("bad-email"))));
    }

    @Test
    void utc053_updateAccount_invalidPhoneThrowsBadRequest() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setPhone("abc");
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateAccount(3L, req)));
    }

    @Test
    void utc054_updateAccount_passwordReencoded() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setPassword("New@1234");
        service.updateAccount(3L, req);
        assertEquals("enc:New@1234", user.getPasswordHash());
    }

    @Test
    void utc055_updateAccount_suspendRevokesActiveRefreshTokens() {
        authenticateAs(admin);
        RefreshToken rt = new RefreshToken();
        rt.setAccount(user);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(3L)).thenReturn(List.of(rt));
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setStatus(AccountStatus.SUSPENDED);

        assertEquals(AccountStatus.SUSPENDED, service.updateAccount(3L, req).getStatus());
        assertNotNull(rt.getRevokedAt());
    }

    @Test
    void utc056_updateAccount_activateDoesNotTouchRefreshTokens() {
        authenticateAs(admin);
        user.setStatus(AccountStatus.SUSPENDED);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setStatus(AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, service.updateAccount(3L, req).getStatus());
        verify(refreshTokenRepository, never()).findByAccount_IdAndRevokedAtIsNull(anyLong());
    }

    @Test
    void utc057_updateAccount_blankFullNameKeepsExisting() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("   ");
        assertEquals("Name 3", service.updateAccount(3L, req).getFullName());
    }

    // ---------------------------------------------------------------- softDeleteAccount

    @Test
    void utc058_softDeleteAccount_adminDeletesUserAndRevokesTokens() {
        authenticateAs(admin);
        RefreshToken rt = new RefreshToken();
        rt.setAccount(user);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(3L)).thenReturn(List.of(rt));

        assertEquals(AccountStatus.DELETED, service.softDeleteAccount(3L).getStatus());
        assertNotNull(rt.getRevokedAt());
    }

    @Test
    void utc059_softDeleteAccount_selfDeleteThrowsBadRequest() {
        authenticateAs(admin);
        AppException ex = assertThrows(AppException.class, () -> service.softDeleteAccount(1L));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("Không thể tự xóa tài khoản đang đăng nhập của chính mình", ex.getMessage());
    }

    @Test
    void utc060_softDeleteAccount_managerDeletingAdminThrowsForbidden() {
        authenticateAs(manager);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.softDeleteAccount(1L)));
    }

    @Test
    void utc061_softDeleteAccount_missingTargetThrowsAccountNotFound() {
        authenticateAs(admin);
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.softDeleteAccount(999L)));
    }

    // ---------------------------------------------------------------- importAccountsFromExcel

    @Test
    void utc062_import_standardHeaderTwoValidRows() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"u1@x.com", "Abc@1234", "User One", "USER"},
                new String[]{"u2@x.com", "Abc@1234", "User Two", "APPROVER"})));
        assertEquals(2, res.getTotalRows());
        assertEquals(2, res.getSuccessCount());
        assertEquals(0, res.getFailedCount());
        assertEquals("Abc@1234", res.getImportedAccounts().get(0).getGeneratedPassword());
    }

    @Test
    void utc063_import_nullFileThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.importAccountsFromExcel(null)));
    }

    @Test
    void utc064_import_emptyFileThrowsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0]);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.importAccountsFromExcel(empty)));
    }

    @Test
    void utc065_import_corruptFileThrowsExcelParseError() {
        MockMultipartFile corrupt = new MockMultipartFile("file", "bad.xlsx", "application/octet-stream",
                "not-an-excel".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.EXCEL_PARSE_ERROR, code(() -> service.importAccountsFromExcel(corrupt)));
    }

    @Test
    void utc066_import_noHeaderRowImportsFromFirstRow() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.<String[]>of(
                new String[]{"a@x.com", "Abc@1234", "A", "USER"})));
        assertEquals(1, res.getSuccessCount());
    }

    @Test
    void utc067_import_reorderedHeaderRemapsColumns() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"full_name", "email", "password"},
                new String[]{"Nguyen A", "remapped@x.com", "Abc@1234"})));
        assertEquals(1, res.getSuccessCount());
        assertEquals("Nguyen A", res.getImportedAccounts().get(0).getFullName());
    }

    @Test
    void utc068_import_blankPasswordGeneratedAndRoleColumnRemapped() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"email", "password", "role", "full_name"},
                new String[]{"rcol@x.com", "", "APPROVER", "A"})));
        assertEquals(AccountRole.APPROVER, res.getImportedAccounts().get(0).getRole());
        assertTrue(res.getImportedAccounts().get(0).isMustChangePassword());
    }

    @Test
    void utc069_import_emptyEmailRowCollectsError() throws IOException {
        ExcelImportResponse res = importOneRow("", "Abc@1234", "NoEmail", "USER");
        assertEquals(0, res.getSuccessCount());
        assertTrue(hasError(res, "Dòng 2: Cột email/gmail không được để trống"), () -> "Errors: " + res.getErrors());
    }

    @Test
    void utc070_import_invalidEmailRowCollectsError() throws IOException {
        ExcelImportResponse res = importOneRow("abc", "Abc@1234", "BadEmail", "USER");
        assertEquals(0, res.getSuccessCount());
        assertTrue(hasError(res, "Dòng 2: Email không hợp lệ: abc"), () -> "Errors: " + res.getErrors());
    }

    @Test
    void utc071_import_duplicateEmailRowCollectsError() throws IOException {
        when(accountRepository.existsByEmailIgnoreCase("exists@x.com")).thenReturn(true);
        ExcelImportResponse res = importOneRow("exists@x.com", "Abc@1234", "Dup", "USER");
        assertEquals(0, res.getSuccessCount());
        assertTrue(hasError(res, "Email 'exists@x.com' đã tồn tại trong DB"), () -> "Errors: " + res.getErrors());
    }

    @Test
    void utc072_import_invalidRoleRowCollectsError() throws IOException {
        ExcelImportResponse res = importOneRow("badrole@x.com", "Abc@1234", "BadRole", "BOSS");
        assertEquals(0, res.getSuccessCount());
        assertTrue(hasError(res, "Role không hợp lệ: BOSS"), () -> "Errors: " + res.getErrors());
    }

    @Test
    void utc073_import_invalidPasswordRowCollectsError() throws IOException {
        ExcelImportResponse res = importOneRow("badpass@x.com", "123", "BadPass", "USER");
        assertEquals(0, res.getSuccessCount());
        assertTrue(hasError(res, "Mật khẩu phải dài từ 8 đến 16 ký tự"), () -> "Errors: " + res.getErrors());
    }

    @Test
    void utc074_import_managerImportingAdminDowngradedToUser() throws IOException {
        authenticateAs(manager);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"tryadmin@x.com", "Abc@1234", "Try", "ADMIN"})));
        assertEquals(AccountRole.USER, res.getImportedAccounts().get(0).getRole());
    }

    @Test
    void utc075_import_emptyFullNameDerivedFromEmailPrefix() throws IOException {
        ExcelImportResponse res = importOneRow("nam.nv@x.com", "Abc@1234", "", "USER");
        assertEquals("nam.nv", res.getImportedAccounts().get(0).getFullName());
    }

    @Test
    void utc076_import_roleWordInNameColumnParsedAsRole() throws IOException {
        ExcelImportResponse res = importOneRow("ap@x.com", "Abc@1234", "APPROVER", "");
        assertEquals(AccountRole.APPROVER, res.getImportedAccounts().get(0).getRole());
        assertEquals("ap", res.getImportedAccounts().get(0).getFullName());
    }

    @Test
    void utc077_import_blankRowSkipped() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"", "", "", ""},
                new String[]{"ok@x.com", "Abc@1234", "Ok", "USER"})));
        assertEquals(1, res.getTotalRows());
        assertEquals(1, res.getSuccessCount());
        assertTrue(res.getErrors().isEmpty());
    }

    @Test
    void utc078_import_invalidRowDoesNotStopLaterValidRows() throws IOException {
        authenticateAs(admin);
        ExcelImportResponse res = service.importAccountsFromExcel(xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"ok1@x.com", "Abc@1234", "Ok1", "USER"},
                new String[]{"abc", "Abc@1234", "BadEmail", "USER"},
                new String[]{"ok2@x.com", "Abc@1234", "Ok2", "USER"})));
        assertEquals(3, res.getTotalRows());
        assertEquals(2, res.getSuccessCount());
        assertEquals(1, res.getFailedCount());
    }

    // ---------------------------------------------------------------- template & random password

    @Test
    void utc079_generateExcelTemplate_hasHeaderAndTwoSampleRows() throws IOException {
        byte[] bytes = service.generateExcelTemplate();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Accounts_Import");
            assertNotNull(sheet);
            assertEquals("email", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("password", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("full_name", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("role", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("user01@gmail.com", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("user02@gmail.com", sheet.getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    void utc080_generateRandomPassword_lengthTenMatchesPolicy() {
        String p = AccountService.generateRandomPassword(10);
        assertEquals(10, p.length());
        assertTrue(p.matches("^(?=.*[^a-zA-Z0-9\\s])\\S{8,16}$"));
        assertTrue(p.chars().anyMatch(ch -> "@#$%!&*".indexOf(ch) >= 0));
    }

    @Test
    void utc081_generateRandomPassword_lengthFourClampedToEight() {
        assertEquals(8, AccountService.generateRandomPassword(4).length());
    }

    @Test
    void utc082_generateRandomPassword_lengthThirtyClampedToSixteen() {
        assertEquals(16, AccountService.generateRandomPassword(30).length());
    }

    @Test
    void utc083_generateRandomPassword_successiveCallsDiffer() {
        assertNotEquals(AccountService.generateRandomPassword(10), AccountService.generateRandomPassword(10));
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorCode code(Executable call) {
        return assertThrows(AppException.class, call).getErrorCode();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(accountRepository).searchAccounts(any(), any(), any(), cap.capture());
        return cap.getValue();
    }

    private AccountDto updateMyPhone(String phone) {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPhone(phone);
        return service.updateMyProfile(req);
    }

    private AccountDto updateMyPassword(String newPassword) {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNewPassword(newPassword);
        return service.updateMyProfile(req);
    }

    private static CreateAccountRequest createReq(String email, String fullName) {
        CreateAccountRequest req = new CreateAccountRequest();
        req.setEmail(email);
        req.setFullName(fullName);
        return req;
    }

    private static UpdateAccountRequest updateEmail(String email) {
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setEmail(email);
        return req;
    }

    private ExcelImportResponse importOneRow(String... row) throws IOException {
        authenticateAs(admin);
        return service.importAccountsFromExcel(xlsx(List.of(new String[]{"email", "password", "full_name", "role"}, row)));
    }

    private static boolean hasError(ExcelImportResponse res, String fragment) {
        return res.getErrors().stream().anyMatch(e -> e.contains(fragment));
    }

    private void authenticateAs(Account actor) {
        CustomUserDetails details = new CustomUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private static List<LoginAuditLog> auditLogs(int count) {
        List<LoginAuditLog> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LoginAuditLog log = new LoginAuditLog();
            log.setId((long) (count - i));
            log.setEventType("LOGIN_PASSWORD");
            log.setSuccess(true);
            log.setDetails(Map.of("idx", i));
            log.setCreatedAt(OffsetDateTime.now().minusMinutes(i));
            list.add(log);
        }
        return list;
    }

    private static MockMultipartFile xlsx(List<String[]> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                String[] cells = rows.get(r);
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "accounts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private static Account account(long id, String email, AccountRole role, AccountStatus status) {
        Account acc = new Account();
        acc.setId(id);
        acc.setEmail(email);
        acc.setFullName("Name " + id);
        acc.setPasswordHash("encoded-hash");
        acc.setRole(role);
        acc.setStatus(status);
        return acc;
    }
}
