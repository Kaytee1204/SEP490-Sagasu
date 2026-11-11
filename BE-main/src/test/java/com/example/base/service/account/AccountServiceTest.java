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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

    @BeforeEach
    void setUp() {
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // UTC001, UTC002, UTC003, UTC004, UTC005, UTC006, UTC007 - getAccountsPage filters, page/size clamping, keyword trimming & empty page
    @Test
    void getAccountsPage_handlesFiltersPaginationBoundsAndKeywordTrimming() {
        List<Account> three = List.of(
                account(1L, "an1@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE),
                account(2L, "an2@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE),
                account(3L, "an3@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE)
        );
        when(accountRepository.countByStatus(AccountStatus.ACTIVE)).thenReturn(10L);
        when(accountRepository.countByStatus(AccountStatus.SUSPENDED)).thenReturn(2L);
        when(accountRepository.countByStatus(AccountStatus.DELETED)).thenReturn(1L);

        // UTC001 - Normal page 1, size 10, keyword="an", role=USER, status=ACTIVE with status counts
        when(accountRepository.searchAccounts(eq("an"), eq(AccountRole.USER), eq(AccountStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(three, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")), 3));
        PageResponse<AccountDto> normal = service.getAccountsPage(1, 10, "an", AccountRole.USER, AccountStatus.ACTIVE, false);
        assertEquals(3, normal.getContent().size());
        assertEquals(1, normal.getPage());
        assertEquals(10, normal.getSize());
        assertEquals(10L, normal.getActiveCount());
        assertEquals(2L, normal.getSuspendedCount());
        assertEquals(1L, normal.getDeletedCount());

        // UTC002 & UTC005 - page=0 clamped to 1 & whitespace keyword="   " converted to null
        when(accountRepository.searchAccounts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));
        service.getAccountsPage(0, 10, "   ", null, null, false);
        // UTC003 & UTC006 - page=-5, size=0 clamped to (1,1) & keyword="  nam  " trimmed to "nam"
        service.getAccountsPage(-5, 0, "  nam  ", null, null, false);
        // UTC004 & UTC007 - size=500 capped at 100 & empty page returns totalElements=0
        PageResponse<AccountDto> emptyRes = service.getAccountsPage(3, 500, null, null, null, false);
        assertTrue(emptyRes.getContent().isEmpty());
        assertEquals(0L, emptyRes.getTotalElements());

        ArgumentCaptor<Pageable> pageCap = ArgumentCaptor.forClass(Pageable.class);
        verify(accountRepository, org.mockito.Mockito.times(2)).searchAccounts(isNull(), isNull(), isNull(), pageCap.capture());
        assertEquals(10, pageCap.getAllValues().get(0).getPageSize());
        assertEquals(100, pageCap.getAllValues().get(1).getPageSize());

        ArgumentCaptor<String> kwCap = ArgumentCaptor.forClass(String.class);
        verify(accountRepository, org.mockito.Mockito.atLeast(2)).searchAccounts(kwCap.capture(), isNull(), isNull(), any());
        assertTrue(kwCap.getAllValues().contains("nam"));
    }

    // UTC008, UTC009, UTC010, UTC011, UTC012, UTC013, UTC014, UTC015, UTC016 - getAccountById, getMyProfile & getMyAuditLogs
    @Test
    void getAccountById_getMyProfile_and_getMyAuditLogs_coverAuthAndLimits() {
        // UTC008 - Existing account id=1 returned as AccountDto
        Account a1 = account(1L, "a@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(a1));
        assertEquals("a@fpt.edu.vn", service.getAccountById(1L).getEmail());

        // UTC009 - Missing account id=999 throws ACCOUNT_NOT_FOUND
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.getAccountById(999L)).getErrorCode());

        // UTC011 & UTC016 - Unauthenticated calls to getMyProfile & getMyAuditLogs throw UNAUTHORIZED
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class, () -> service.getMyProfile()).getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class, () -> service.getMyAuditLogs()).getErrorCode());

        // UTC010 - Authenticated getMyProfile returns actor DTO
        authenticateAs(a1);
        assertEquals(1L, service.getMyProfile().getId());

        // UTC013 - 5 audit logs mapped in order with expected fields
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(1L)).thenReturn(auditLogs(5));
        List<Map<String, Object>> five = service.getMyAuditLogs();
        assertEquals(5, five.size());
        assertTrue(five.get(0).containsKey("eventType"));

        // UTC014 - 45 audit logs capped at 30 items
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(1L)).thenReturn(auditLogs(45));
        assertEquals(30, service.getMyAuditLogs().size());

        // UTC015 - 0 audit logs returns empty list
        when(loginAuditLogRepository.findByAccount_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertTrue(service.getMyAuditLogs().isEmpty());

        // UTC012 - Authenticated user deleted from DB throws UNAUTHORIZED
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class, () -> service.getMyProfile()).getErrorCode());
    }

    // UTC017, UTC018, UTC019, UTC020, UTC021, UTC022, UTC023, UTC024, UTC025, UTC026, UTC027, UTC028, UTC029, UTC030, UTC031, UTC032 - updateMyProfile validation & role rules
    @Test
    void updateMyProfile_validatesPhonePasswordBoundariesRoleAndAuth() {
        // UTC032 - Unauthenticated updateMyProfile throws UNAUTHORIZED
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class, () -> service.updateMyProfile(new UpdateProfileRequest())).getErrorCode());

        Account user = account(1L, "u@fpt.edu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        user.setMustChangePassword(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(user));
        authenticateAs(user);

        // UTC017 - Valid local phone "0912345678" updates phone & logs PROFILE_UPDATED
        UpdateProfileRequest phone1 = new UpdateProfileRequest();
        phone1.setPhone("0912345678");
        assertEquals("0912345678", service.updateMyProfile(phone1).getPhone());
        ArgumentCaptor<LoginAuditLog> logCap = ArgumentCaptor.forClass(LoginAuditLog.class);
        verify(loginAuditLogRepository).save(logCap.capture());
        assertEquals("PROFILE_UPDATED", logCap.getValue().getEventType());
        assertEquals(true, logCap.getValue().getDetails().get("phoneUpdated"));
        assertEquals(false, logCap.getValue().getDetails().get("passwordChanged"));

        // UTC018 - Valid international phone "+84912345678" accepted
        UpdateProfileRequest phone2 = new UpdateProfileRequest();
        phone2.setPhone("+84912345678");
        assertEquals("+84912345678", service.updateMyProfile(phone2).getPhone());

        // UTC019 - Invalid short phone "12345" throws BAD_REQUEST
        UpdateProfileRequest shortPhone = new UpdateProfileRequest();
        shortPhone.setPhone("12345");
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.updateMyProfile(shortPhone)).getErrorCode());

        // UTC020 - Boundary 9-digit phone "091234567" throws BAD_REQUEST
        UpdateProfileRequest nineDigits = new UpdateProfileRequest();
        nineDigits.setPhone("091234567");
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.updateMyProfile(nineDigits)).getErrorCode());

        // UTC021 - Blank phone "   " clears phone to ""
        UpdateProfileRequest clearPhone = new UpdateProfileRequest();
        clearPhone.setPhone("   ");
        assertEquals("", service.updateMyProfile(clearPhone).getPhone());

        // UTC022 - Valid password change re-encodes password & clears mustChangePassword
        UpdateProfileRequest changePass = new UpdateProfileRequest();
        changePass.setCurrentPassword("Old@1234");
        changePass.setNewPassword("New@1234");
        when(passwordEncoder.matches("Old@1234", user.getPasswordHash())).thenReturn(true);
        AccountDto afterPass = service.updateMyProfile(changePass);
        assertFalse(afterPass.isMustChangePassword());
        assertEquals("enc:New@1234", user.getPasswordHash());

        // UTC023 - Wrong currentPassword throws INVALID_CREDENTIALS
        UpdateProfileRequest wrongCurrent = new UpdateProfileRequest();
        wrongCurrent.setCurrentPassword("wrong");
        wrongCurrent.setNewPassword("New@1234");
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);
        assertEquals(ErrorCode.INVALID_CREDENTIALS,
                assertThrows(AppException.class, () -> service.updateMyProfile(wrongCurrent)).getErrorCode());

        // UTC024, UTC025 & UTC028 - Rejects missing special char (UTC024), 7-char (UTC025) & 17-char (UTC028) passwords
        for (String badPass : List.of("abc12345", "Ab@1234", "Ab@12345678901234")) {
            UpdateProfileRequest invalidPassReq = new UpdateProfileRequest();
            invalidPassReq.setNewPassword(badPass);
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(AppException.class, () -> service.updateMyProfile(invalidPassReq)).getErrorCode());
        }
        // UTC026 & UTC027 - Accepts 8-char & 16-char boundary passwords
        for (String okPass : List.of("Ab@12345", "Ab@1234567890123")) {
            UpdateProfileRequest validBoundary = new UpdateProfileRequest();
            validBoundary.setNewPassword(okPass);
            assertNotNull(service.updateMyProfile(validBoundary));
        }

        // UTC029 - Non-ADMIN USER changing role to ADMIN throws FORBIDDEN
        UpdateProfileRequest roleEsc = new UpdateProfileRequest();
        roleEsc.setRole(AccountRole.ADMIN);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.updateMyProfile(roleEsc)).getErrorCode());

        // UTC030 - USER keeping same role succeeds
        UpdateProfileRequest sameRole = new UpdateProfileRequest();
        sameRole.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.updateMyProfile(sameRole).getRole());

        // UTC031 - ADMIN may change own role
        Account admin = account(2L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(admin));
        authenticateAs(admin);
        UpdateProfileRequest adminChangeRole = new UpdateProfileRequest();
        adminChangeRole.setRole(AccountRole.MANAGER);
        assertEquals(AccountRole.MANAGER, service.updateMyProfile(adminChangeRole).getRole());
    }

    // UTC033, UTC034, UTC035, UTC036, UTC037, UTC038, UTC039, UTC040, UTC041, UTC042 - createAccount email, password, role hierarchy & defaults
    @Test
    void createAccount_validatesEmailPasswordRoleHierarchyAndDefaults() {
        Account admin = account(1L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(admin));
        authenticateAs(admin);

        // UTC033 - Valid ADMIN creation normalizes email, trims fullName, sets APPROVER & ACTIVE
        CreateAccountRequest req1 = new CreateAccountRequest();
        req1.setEmail("New@FPT.edu.vn");
        req1.setFullName(" Nguyen A ");
        req1.setPassword("Abc@1234");
        req1.setRole(AccountRole.APPROVER);
        AccountDto created1 = service.createAccount(req1);
        assertEquals("new@fpt.edu.vn", created1.getEmail());
        assertEquals("Nguyen A", created1.getFullName());
        assertEquals(AccountRole.APPROVER, created1.getRole());
        assertEquals(AccountStatus.ACTIVE, created1.getStatus());
        assertFalse(created1.isMustChangePassword());
        assertEquals("Abc@1234", created1.getGeneratedPassword());

        // UTC034 & UTC035 - Null password generates 10-char password & mustChangePassword=true; null role defaults to USER
        CreateAccountRequest reqRandom = new CreateAccountRequest();
        reqRandom.setEmail("rand@fpt.edu.vn");
        reqRandom.setFullName("Rand");
        reqRandom.setRole(null);
        reqRandom.setPassword(null);
        reqRandom.setMustChangePassword(null);
        AccountDto createdRand = service.createAccount(reqRandom);
        assertEquals(AccountRole.USER, createdRand.getRole());
        assertTrue(createdRand.isMustChangePassword());
        assertEquals(10, createdRand.getGeneratedPassword().length());

        // UTC042 - Custom SUSPENDED status, validUntil & createdById recorded
        OffsetDateTime validUntil = OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        CreateAccountRequest reqSuspended = new CreateAccountRequest();
        reqSuspended.setEmail("susp@fpt.edu.vn");
        reqSuspended.setFullName("Susp");
        reqSuspended.setPassword("Abc@1234");
        reqSuspended.setStatus(AccountStatus.SUSPENDED);
        reqSuspended.setValidUntil(validUntil);
        AccountDto createdSusp = service.createAccount(reqSuspended);
        assertEquals(AccountStatus.SUSPENDED, createdSusp.getStatus());
        assertEquals(validUntil, createdSusp.getValidUntil());
        assertEquals(1L, createdSusp.getCreatedById());

        // UTC038 & UTC039 - Malformed email "abc" & 1-char TLD "a@b.c" throw BAD_REQUEST
        for (String badEmail : List.of("abc", "a@b.c")) {
            CreateAccountRequest bad = new CreateAccountRequest();
            bad.setEmail(badEmail);
            bad.setFullName("X");
            assertEquals(ErrorCode.BAD_REQUEST,
                    assertThrows(AppException.class, () -> service.createAccount(bad)).getErrorCode());
        }

        // UTC040 - Duplicate email throws EMAIL_ALREADY_EXISTS
        CreateAccountRequest dup = new CreateAccountRequest();
        dup.setEmail("dup@fpt.edu.vn");
        dup.setFullName("Dup");
        when(accountRepository.existsByEmailIgnoreCase("dup@fpt.edu.vn")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS,
                assertThrows(AppException.class, () -> service.createAccount(dup)).getErrorCode());

        // UTC041 - Invalid short password throws BAD_REQUEST
        CreateAccountRequest shortPass = new CreateAccountRequest();
        shortPass.setEmail("ok@fpt.edu.vn");
        shortPass.setFullName("Ok");
        shortPass.setPassword("short");
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.createAccount(shortPass)).getErrorCode());

        Account manager = account(2L, "mgr@sagasu.vn", AccountRole.MANAGER, AccountStatus.ACTIVE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(manager));
        authenticateAs(manager);

        // UTC036 - MANAGER assigning role != USER throws FORBIDDEN
        CreateAccountRequest mgrApprover = new CreateAccountRequest();
        mgrApprover.setEmail("ap@fpt.edu.vn");
        mgrApprover.setFullName("Ap");
        mgrApprover.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.createAccount(mgrApprover)).getErrorCode());

        // UTC037 - MANAGER creating role USER succeeds
        CreateAccountRequest mgrUser = new CreateAccountRequest();
        mgrUser.setEmail("u@fpt.edu.vn");
        mgrUser.setFullName("U");
        mgrUser.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.createAccount(mgrUser).getRole());
    }

    // UTC043, UTC044, UTC045, UTC046, UTC047, UTC048, UTC049, UTC050, UTC051, UTC052, UTC053, UTC054, UTC055, UTC056, UTC057 - updateAccount hierarchy, validation & token revocation
    @Test
    void updateAccount_validatesHierarchyRoleEmailPhonePasswordAndStatusRevocation() {
        Account admin = account(1L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        Account manager = account(2L, "mgr@sagasu.vn", AccountRole.MANAGER, AccountStatus.ACTIVE);
        Account user = account(3L, "a@x.com", AccountRole.USER, AccountStatus.ACTIVE);
        user.setFullName("Initial Name");
        Account approver = account(4L, "ap@x.com", AccountRole.APPROVER, AccountStatus.ACTIVE);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(user));
        when(accountRepository.findById(4L)).thenReturn(Optional.of(approver));
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // UTC043 - ADMIN updates USER fullName & phone
        authenticateAs(admin);
        UpdateAccountRequest basic = new UpdateAccountRequest();
        basic.setFullName("Tran B");
        basic.setPhone("0987654321");
        AccountDto updated = service.updateAccount(3L, basic);
        assertEquals("Tran B", updated.getFullName());
        assertEquals("0987654321", updated.getPhone());

        // UTC044 - Target id=999 missing throws ACCOUNT_NOT_FOUND
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.updateAccount(999L, basic)).getErrorCode());

        // UTC050 - Same email differing only in case does not trigger duplicate check
        UpdateAccountRequest sameEmail = new UpdateAccountRequest();
        sameEmail.setEmail("A@X.COM");
        assertEquals("a@x.com", service.updateAccount(3L, sameEmail).getEmail());

        // UTC051 - Special email "admin" is accepted
        UpdateAccountRequest adminEmail = new UpdateAccountRequest();
        adminEmail.setEmail("admin");
        assertEquals("admin", service.updateAccount(3L, adminEmail).getEmail());
        user.setEmail("a@x.com");

        // UTC049 - Email already owned by another account throws EMAIL_ALREADY_EXISTS
        UpdateAccountRequest takenEmail = new UpdateAccountRequest();
        takenEmail.setEmail("taken@x.com");
        when(accountRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS,
                assertThrows(AppException.class, () -> service.updateAccount(3L, takenEmail)).getErrorCode());

        // UTC052 - Malformed email throws BAD_REQUEST
        UpdateAccountRequest badEmail = new UpdateAccountRequest();
        badEmail.setEmail("bad-email");
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.updateAccount(3L, badEmail)).getErrorCode());

        // UTC053 - Invalid phone throws BAD_REQUEST
        UpdateAccountRequest badPhone = new UpdateAccountRequest();
        badPhone.setPhone("abc");
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.updateAccount(3L, badPhone)).getErrorCode());

        // UTC054 - Password update re-encodes newPassword
        UpdateAccountRequest newPass = new UpdateAccountRequest();
        newPass.setPassword("New@1234");
        service.updateAccount(3L, newPass);
        assertEquals("enc:New@1234", user.getPasswordHash());

        // UTC055 - Setting status=SUSPENDED revokes all active refresh tokens
        RefreshToken rt = new RefreshToken();
        rt.setAccount(user);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(3L)).thenReturn(List.of(rt));
        UpdateAccountRequest suspend = new UpdateAccountRequest();
        suspend.setStatus(AccountStatus.SUSPENDED);
        assertEquals(AccountStatus.SUSPENDED, service.updateAccount(3L, suspend).getStatus());
        assertNotNull(rt.getRevokedAt());

        // UTC056 - Setting status=ACTIVE does not query/revoke refresh tokens
        UpdateAccountRequest activate = new UpdateAccountRequest();
        activate.setStatus(AccountStatus.ACTIVE);
        assertEquals(AccountStatus.ACTIVE, service.updateAccount(3L, activate).getStatus());
        verify(refreshTokenRepository).findByAccount_IdAndRevokedAtIsNull(3L);

        // UTC057 - Blank fullName leaves existing fullName unchanged
        UpdateAccountRequest blankName = new UpdateAccountRequest();
        blankName.setFullName("   ");
        assertEquals("Tran B", service.updateAccount(3L, blankName).getFullName());

        // UTC048 - ADMIN changes USER role to APPROVER
        UpdateAccountRequest roleUp = new UpdateAccountRequest();
        roleUp.setRole(AccountRole.APPROVER);
        assertEquals(AccountRole.APPROVER, service.updateAccount(3L, roleUp).getRole());
        user.setRole(AccountRole.USER);

        // UTC045 - MANAGER targeting ADMIN throws FORBIDDEN
        authenticateAs(manager);
        UpdateAccountRequest mgrOnAdmin = new UpdateAccountRequest();
        mgrOnAdmin.setFullName("X");
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.updateAccount(1L, mgrOnAdmin)).getErrorCode());

        // UTC046 - MANAGER may update APPROVER account
        assertEquals("X", service.updateAccount(4L, mgrOnAdmin).getFullName());

        // UTC047 - MANAGER changing USER role throws FORBIDDEN
        UpdateAccountRequest mgrRoleChange = new UpdateAccountRequest();
        mgrRoleChange.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.updateAccount(3L, mgrRoleChange)).getErrorCode());
    }

    // UTC058, UTC059, UTC060, UTC061 - softDeleteAccount enforces self-delete block, hierarchy & revokes refresh tokens
    @Test
    void softDeleteAccount_enforcesSelfDeleteHierarchyAndRevokesTokens() {
        Account admin = account(1L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        Account user = account(2L, "user@sagasu.vn", AccountRole.USER, AccountStatus.ACTIVE);
        Account manager = account(3L, "mgr@sagasu.vn", AccountRole.MANAGER, AccountStatus.ACTIVE);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(user));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(manager));
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // UTC058 - ADMIN deletes USER -> status=DELETED & refresh tokens revoked
        authenticateAs(admin);
        RefreshToken rt = new RefreshToken();
        rt.setAccount(user);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(2L)).thenReturn(List.of(rt));

        AccountDto deleted = service.softDeleteAccount(2L);
        assertEquals(AccountStatus.DELETED, deleted.getStatus());
        assertNotNull(rt.getRevokedAt());

        // UTC059 - Self-delete throws BAD_REQUEST
        AppException selfDel = assertThrows(AppException.class, () -> service.softDeleteAccount(1L));
        assertEquals(ErrorCode.BAD_REQUEST, selfDel.getErrorCode());
        assertEquals("Không thể tự xóa tài khoản đang đăng nhập của chính mình", selfDel.getMessage());

        // UTC061 - Missing target id=999 throws ACCOUNT_NOT_FOUND
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.softDeleteAccount(999L)).getErrorCode());

        // UTC060 - MANAGER deleting ADMIN throws FORBIDDEN
        authenticateAs(manager);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.softDeleteAccount(1L)).getErrorCode());
    }

    // UTC062, UTC066, UTC067, UTC068, UTC074, UTC075, UTC076, UTC077 - importAccountsFromExcel handles standard, no-header, remapped & role-downgrade imports
    @Test
    void importAccountsFromExcel_whenValidHeadersRemappedOrNoHeader_importsAccounts() throws IOException {
        Account admin = account(1L, "admin@sagasu.vn", AccountRole.ADMIN, AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(admin));
        authenticateAs(admin);

        // UTC062 - Standard header email|password|full_name|role with 2 valid rows
        MockMultipartFile standard = xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"u1@x.com", "Abc@1234", "User One", "USER"},
                new String[]{"u2@x.com", "Abc@1234", "User Two", "APPROVER"}
        ));
        ExcelImportResponse stdRes = service.importAccountsFromExcel(standard);
        assertEquals(2, stdRes.getTotalRows());
        assertEquals(2, stdRes.getSuccessCount());
        assertEquals(0, stdRes.getFailedCount());
        assertEquals("Abc@1234", stdRes.getImportedAccounts().get(0).getGeneratedPassword());

        // UTC066 - File without header row starts importing at row 0
        MockMultipartFile noHeader = xlsx(List.<String[]>of(
                new String[]{"a@x.com", "Abc@1234", "A", "USER"}
        ));
        assertEquals(1, service.importAccountsFromExcel(noHeader).getSuccessCount());

        // UTC067 - Header full_name|email|password remaps column indices accurately
        MockMultipartFile remapped = xlsx(List.of(
                new String[]{"full_name", "email", "password"},
                new String[]{"Nguyen A", "remapped@x.com", "Abc@1234"}
        ));
        ExcelImportResponse remapRes = service.importAccountsFromExcel(remapped);
        assertEquals(1, remapRes.getSuccessCount());
        assertEquals("Nguyen A", remapRes.getImportedAccounts().get(0).getFullName());

        // UTC068 - Header email|password|role|full_name with blank password generates password & mustChangePassword=true
        MockMultipartFile roleCol2 = xlsx(List.of(
                new String[]{"email", "password", "role", "full_name"},
                new String[]{"rcol@x.com", "", "APPROVER", "A"}
        ));
        ExcelImportResponse roleCol2Res = service.importAccountsFromExcel(roleCol2);
        assertEquals(AccountRole.APPROVER, roleCol2Res.getImportedAccounts().get(0).getRole());
        assertTrue(roleCol2Res.getImportedAccounts().get(0).isMustChangePassword());

        // UTC077, UTC075 & UTC076 - Blank row skipped (UTC077); empty full_name derives prefix (UTC075); role word in name column parsed & name defaults to prefix (UTC076)
        MockMultipartFile prefixNameAndRoleInNameColAndBlankRow = xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"", "", "", ""},
                new String[]{"nam.nv@x.com", "Abc@1234", "", "USER"},
                new String[]{"ap@x.com", "Abc@1234", "APPROVER", ""}
        ));
        ExcelImportResponse edgeRes = service.importAccountsFromExcel(prefixNameAndRoleInNameColAndBlankRow);
        assertEquals(2, edgeRes.getTotalRows());
        assertEquals("nam.nv", edgeRes.getImportedAccounts().get(0).getFullName());
        assertEquals(AccountRole.APPROVER, edgeRes.getImportedAccounts().get(1).getRole());
        assertEquals("ap", edgeRes.getImportedAccounts().get(1).getFullName());

        // UTC074 - MANAGER importing role=ADMIN downgrades imported account to USER
        Account manager = account(2L, "mgr@sagasu.vn", AccountRole.MANAGER, AccountStatus.ACTIVE);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(manager));
        authenticateAs(manager);
        MockMultipartFile mgrDown = xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"tryadmin@x.com", "Abc@1234", "Try", "ADMIN"}
        ));
        assertEquals(AccountRole.USER, service.importAccountsFromExcel(mgrDown).getImportedAccounts().get(0).getRole());
    }

    // UTC063, UTC064, UTC065, UTC069, UTC070, UTC071, UTC072, UTC073, UTC078 - importAccountsFromExcel file errors & per-row error collection
    @Test
    void importAccountsFromExcel_whenInvalidFileOrRowErrors_throwsOrCollectsErrors() throws IOException {
        // UTC063 - Null file throws BAD_REQUEST
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.importAccountsFromExcel(null)).getErrorCode());

        // UTC064 - Empty 0-byte file throws BAD_REQUEST
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0]);
        assertEquals(ErrorCode.BAD_REQUEST,
                assertThrows(AppException.class, () -> service.importAccountsFromExcel(empty)).getErrorCode());

        // UTC065 - Corrupt non-Excel file throws EXCEL_PARSE_ERROR
        MockMultipartFile corrupt = new MockMultipartFile("file", "bad.xlsx", "application/octet-stream",
                "not-an-excel".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.EXCEL_PARSE_ERROR,
                assertThrows(AppException.class, () -> service.importAccountsFromExcel(corrupt)).getErrorCode());

        // UTC069-UTC073 & UTC078 - Mixed valid/invalid rows collect per-row errors while still importing valid rows
        when(accountRepository.existsByEmailIgnoreCase(anyString()))
                .thenAnswer(inv -> "exists@x.com".equalsIgnoreCase(inv.getArgument(0)));
        MockMultipartFile mixedErrors = xlsx(List.of(
                new String[]{"email", "password", "full_name", "role"},
                new String[]{"ok1@x.com", "Abc@1234", "Ok1", "USER"},
                new String[]{"", "Abc@1234", "NoEmail", "USER"},       // UTC069 - empty email
                new String[]{"abc", "Abc@1234", "BadEmail", "USER"},    // UTC070 - invalid email
                new String[]{"exists@x.com", "Abc@1234", "Dup", "USER"},// UTC071 - duplicate email
                new String[]{"badrole@x.com", "Abc@1234", "BadRole", "BOSS"}, // UTC072 - invalid role
                new String[]{"badpass@x.com", "123", "BadPass", "USER"},// UTC073 - invalid password
                new String[]{"ok2@x.com", "Abc@1234", "Ok2", "USER"}    // UTC078 - continues importing
        ));

        ExcelImportResponse res = service.importAccountsFromExcel(mixedErrors);
        assertEquals(7, res.getTotalRows());
        assertEquals(2, res.getSuccessCount(), () -> "Errors: " + res.getErrors());
        assertEquals(5, res.getFailedCount());
        assertTrue(res.getErrors().stream().anyMatch(e -> e.contains("Dòng 3: Cột email/gmail không được để trống")));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.contains("Dòng 4: Email không hợp lệ: abc")));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.contains("Email 'exists@x.com' đã tồn tại trong DB")));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.contains("Role không hợp lệ: BOSS")));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.contains("Mật khẩu phải dài từ 8 đến 16 ký tự")));
    }

    // UTC079, UTC080, UTC081, UTC082, UTC083 - generateExcelTemplate workbook structure & generateRandomPassword clamping/uniqueness
    @Test
    void generateExcelTemplate_and_generateRandomPassword_produceValidOutputs() throws IOException {
        // UTC079 - generateExcelTemplate produces Accounts_Import sheet with header & 2 sample rows
        byte[] templateBytes = service.generateExcelTemplate();
        assertTrue(templateBytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
            Sheet sheet = wb.getSheet("Accounts_Import");
            assertNotNull(sheet);
            assertEquals("email", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("password", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("full_name", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("role", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("user01@gmail.com", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("user02@gmail.com", sheet.getRow(2).getCell(0).getStringCellValue());
        }

        // UTC080 - Length 10 password matches policy regex & contains special character
        String p10 = AccountService.generateRandomPassword(10);
        assertEquals(10, p10.length());
        assertTrue(p10.matches("^(?=.*[^a-zA-Z0-9\\s])\\S{8,16}$"));
        assertTrue(p10.chars().anyMatch(ch -> "@#$%!&*".indexOf(ch) >= 0));
        // UTC081 - Length 4 clamped to 8
        assertEquals(8, AccountService.generateRandomPassword(4).length());
        // UTC082 - Length 30 clamped to 16
        assertEquals(16, AccountService.generateRandomPassword(30).length());

        // UTC083 - Successive calls produce distinct random passwords
        String p10b = AccountService.generateRandomPassword(10);
        assertNotEquals(p10, p10b);
    }

    private void authenticateAs(Account actor) {
        CustomUserDetails details = new CustomUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())
        );
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

