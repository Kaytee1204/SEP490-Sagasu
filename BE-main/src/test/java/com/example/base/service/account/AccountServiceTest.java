package com.example.base.service.account;

import com.example.base.dto.account.AccountDto;
import com.example.base.dto.account.CreateAccountRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests UTC039-UTC085 (sheet "Account"): updateMyProfile, createAccount, updateAccount. */
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

    // ---------------------------------------------------------------- updateMyProfile

    @Test
    void utc039_updateMyProfile_validLocalPhoneUpdatedAndLogged() {
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
    void utc040_updateMyProfile_internationalPhoneAccepted() {
        assertEquals("+84912345678", updateMyPhone("+84912345678").getPhone());
    }

    @Test
    void utc041_updateMyProfile_shortPhoneThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("12345")));
    }

    @Test
    void utc042_updateMyProfile_nineDigitPhoneThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("091234567")));
    }

    // Upper boundary of the SRS UC-07 rule (Vietnamese 10-digit number).
    // Expected to fail until defect ACC-DE01 is fixed: PHONE_REGEX ^(0|\+84)[0-9]{9,10}$ still accepts 10 digits after the prefix.
    @Test
    void utc043_updateMyProfile_zeroPlusTenDigitsThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("09123456789")));
    }

    @Test
    void utc044_updateMyProfile_plus84PlusTenDigitsThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPhone("+849123456789")));
    }

    @Test
    void utc045_updateMyProfile_blankPhoneClearsPhone() {
        assertEquals("", updateMyPhone("   ").getPhone());
    }

    @Test
    void utc046_updateMyProfile_validPasswordChangeReencodesAndClearsMustChange() {
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
    void utc047_updateMyProfile_wrongCurrentPasswordThrowsInvalidCredentials() {
        authenticateAs(user);
        when(passwordEncoder.matches("wrong", "encoded-hash")).thenReturn(false);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("New@1234");
        assertEquals(ErrorCode.INVALID_CREDENTIALS, code(() -> service.updateMyProfile(req)));
    }

    @Test
    void utc048_updateMyProfile_passwordWithoutSpecialCharThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("abc12345")));
    }

    @Test
    void utc049_updateMyProfile_sevenCharPasswordThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("Ab@1234")));
    }

    @Test
    void utc050_updateMyProfile_eightCharPasswordAccepted() {
        assertNotNull(updateMyPassword("Ab@12345"));
        assertEquals("enc:Ab@12345", user.getPasswordHash());
    }

    @Test
    void utc051_updateMyProfile_sixteenCharPasswordAccepted() {
        assertNotNull(updateMyPassword("Ab@1234567890123"));
        assertEquals("enc:Ab@1234567890123", user.getPasswordHash());
    }

    @Test
    void utc052_updateMyProfile_seventeenCharPasswordThrowsBadRequest() {
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> updateMyPassword("Ab@12345678901234")));
    }

    // SRS UC-08: current password required (fails until ACC-DE02 is fixed), new password must differ from it,
    // other sessions are signed out (fails until ACC-DE03 is fixed).
    @Test
    void utc053_updateMyProfile_newPasswordWithoutCurrentPasswordThrowsBadRequest() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNewPassword("New@1234");

        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateMyProfile(req)));
        assertEquals("encoded-hash", user.getPasswordHash());
    }

    @Test
    void utc054_updateMyProfile_newPasswordSameAsCurrentThrowsBadRequest() {
        authenticateAs(user);
        when(passwordEncoder.matches("Old@1234", "encoded-hash")).thenReturn(true);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("Old@1234");
        req.setNewPassword("Old@1234");

        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateMyProfile(req)));
        assertEquals("encoded-hash", user.getPasswordHash());
    }

    @Test
    void utc055_updateMyProfile_passwordChangeRevokesRefreshTokens() {
        RefreshToken rt = new RefreshToken();
        rt.setAccount(user);
        when(refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(3L)).thenReturn(List.of(rt));

        updateMyPassword("New@1234");

        assertNotNull(rt.getRevokedAt());
    }

    @Test
    void utc056_updateMyProfile_userEscalatingToAdminThrowsForbidden() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.ADMIN);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateMyProfile(req)));
    }

    @Test
    void utc057_updateMyProfile_userKeepingSameRoleSucceeds() {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.updateMyProfile(req).getRole());
    }

    @Test
    void utc058_updateMyProfile_adminMayChangeOwnRole() {
        authenticateAs(admin);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setRole(AccountRole.MANAGER);
        assertEquals(AccountRole.MANAGER, service.updateMyProfile(req).getRole());
    }

    @Test
    void utc059_updateMyProfile_unauthenticatedThrowsUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.updateMyProfile(new UpdateProfileRequest())));
    }

    // ---------------------------------------------------------------- createAccount

    @Test
    void utc060_createAccount_adminCreatesNormalizedActiveAccount() {
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
    void utc061_createAccount_nullPasswordGeneratesTenCharsAndForcesChange() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("rand@gmail.com", "Rand");
        req.setPassword(null);
        req.setMustChangePassword(null);

        AccountDto dto = service.createAccount(req);

        assertTrue(dto.isMustChangePassword());
        assertEquals(10, dto.getGeneratedPassword().length());
    }

    @Test
    void utc062_createAccount_nullRoleDefaultsToUser() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("rand@gmail.com", "Rand");
        req.setRole(null);
        assertEquals(AccountRole.USER, service.createAccount(req).getRole());
    }

    @Test
    void utc063_createAccount_managerAssigningNonUserRoleThrowsForbidden() {
        authenticateAs(manager);
        CreateAccountRequest req = createReq("ap@gmail.com", "Ap");
        req.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.createAccount(req)));
    }

    @Test
    void utc064_createAccount_managerCreatingUserSucceeds() {
        authenticateAs(manager);
        CreateAccountRequest req = createReq("u@gmail.com", "U");
        req.setRole(AccountRole.USER);
        assertEquals(AccountRole.USER, service.createAccount(req).getRole());
    }

    @Test
    void utc065_createAccount_malformedEmailThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(createReq("abc", "X"))));
    }

    @Test
    void utc066_createAccount_oneCharTldThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(createReq("a@b.c", "X"))));
    }

    @Test
    void utc067_createAccount_duplicateEmailThrowsEmailAlreadyExists() {
        authenticateAs(admin);
        when(accountRepository.existsByEmailIgnoreCase("dup@gmail.com")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, code(() -> service.createAccount(createReq("dup@gmail.com", "Dup"))));
    }

    @Test
    void utc068_createAccount_shortPasswordThrowsBadRequest() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("ok@gmail.com", "Ok");
        req.setPassword("short");
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(req)));
    }

    @Test
    void utc069_createAccount_customStatusValidUntilAndCreatorRecorded() {
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

    // SRS UC-10 step 5, E2: fails until ACC-DE04 is fixed.
    @Test
    void utc070_createAccount_validUntilInPastThrowsBadRequest() {
        authenticateAs(admin);
        CreateAccountRequest req = createReq("past@gmail.com", "Past");
        req.setPassword("Abc@1234");
        req.setValidUntil(OffsetDateTime.now().minusDays(1));

        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.createAccount(req)));
        verify(accountRepository, never()).save(any(Account.class));
    }

    // ---------------------------------------------------------------- updateAccount

    @Test
    void utc071_updateAccount_adminUpdatesFullNameAndPhone() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("Tran B");
        req.setPhone("0987654321");
        AccountDto dto = service.updateAccount(3L, req);
        assertEquals("Tran B", dto.getFullName());
        assertEquals("0987654321", dto.getPhone());
    }

    @Test
    void utc072_updateAccount_missingTargetThrowsAccountNotFound() {
        authenticateAs(admin);
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, code(() -> service.updateAccount(999L, new UpdateAccountRequest())));
    }

    @Test
    void utc073_updateAccount_managerTargetingAdminThrowsForbidden() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("X");
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateAccount(1L, req)));
    }

    @Test
    void utc074_updateAccount_managerMayUpdateApprover() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("X");
        assertEquals("X", service.updateAccount(4L, req).getFullName());
    }

    @Test
    void utc075_updateAccount_managerChangingRoleThrowsForbidden() {
        authenticateAs(manager);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setRole(AccountRole.APPROVER);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.updateAccount(3L, req)));
    }

    @Test
    void utc076_updateAccount_adminChangesUserRoleToApprover() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setRole(AccountRole.APPROVER);
        assertEquals(AccountRole.APPROVER, service.updateAccount(3L, req).getRole());
    }

    @Test
    void utc077_updateAccount_emailOwnedByAnotherThrowsEmailAlreadyExists() {
        authenticateAs(admin);
        when(accountRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, code(() -> service.updateAccount(3L, updateEmail("taken@x.com"))));
    }

    @Test
    void utc078_updateAccount_sameEmailDifferentCaseSkipsDuplicateCheck() {
        authenticateAs(admin);
        assertEquals("a@x.com", service.updateAccount(3L, updateEmail("A@X.COM")).getEmail());
        verify(accountRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void utc079_updateAccount_specialAdminEmailAccepted() {
        authenticateAs(admin);
        assertEquals("admin", service.updateAccount(3L, updateEmail("admin")).getEmail());
    }

    @Test
    void utc080_updateAccount_malformedEmailThrowsBadRequest() {
        authenticateAs(admin);
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateAccount(3L, updateEmail("bad-email"))));
    }

    @Test
    void utc081_updateAccount_invalidPhoneThrowsBadRequest() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setPhone("abc");
        assertEquals(ErrorCode.BAD_REQUEST, code(() -> service.updateAccount(3L, req)));
    }

    @Test
    void utc082_updateAccount_passwordReencoded() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setPassword("New@1234");
        service.updateAccount(3L, req);
        assertEquals("enc:New@1234", user.getPasswordHash());
    }

    @Test
    void utc083_updateAccount_suspendRevokesActiveRefreshTokens() {
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
    void utc084_updateAccount_activateDoesNotTouchRefreshTokens() {
        authenticateAs(admin);
        user.setStatus(AccountStatus.SUSPENDED);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setStatus(AccountStatus.ACTIVE);

        assertEquals(AccountStatus.ACTIVE, service.updateAccount(3L, req).getStatus());
        verify(refreshTokenRepository, never()).findByAccount_IdAndRevokedAtIsNull(anyLong());
    }

    @Test
    void utc085_updateAccount_blankFullNameKeepsExisting() {
        authenticateAs(admin);
        UpdateAccountRequest req = new UpdateAccountRequest();
        req.setFullName("   ");
        assertEquals("Name 3", service.updateAccount(3L, req).getFullName());
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorCode code(Executable call) {
        return assertThrows(AppException.class, call).getErrorCode();
    }

    private AccountDto updateMyPhone(String phone) {
        authenticateAs(user);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPhone(phone);
        return service.updateMyProfile(req);
    }

    private AccountDto updateMyPassword(String newPassword) {
        authenticateAs(user);
        when(passwordEncoder.matches("Old@1234", "encoded-hash")).thenReturn(true);
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("Old@1234");
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

    private void authenticateAs(Account actor) {
        CustomUserDetails details = new CustomUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
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
