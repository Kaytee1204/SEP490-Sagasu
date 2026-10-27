package com.example.base.service.account;

import com.example.base.dto.PageResponse;
import com.example.base.dto.account.AccountDto;
import com.example.base.dto.account.CreateAccountRequest;
import com.example.base.dto.account.ExcelImportResponse;
import com.example.base.dto.account.UpdateAccountRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.RefreshToken;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.AccountMapper;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.RefreshTokenRepository;
import com.example.base.security.CustomUserDetails;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AccountService {

    private static final String RANDOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<AccountRole> MANAGER_ALLOWED_ROLES = EnumSet.of(
            AccountRole.USER,
            AccountRole.EXTERNAL_USER,
            AccountRole.CHECKER,
            AccountRole.APPROVER
    );

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            AccountRepository accountRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountDto> getAccountsPage(
            int page,
            int size,
            String keyword,
            AccountRole role,
            AccountStatus status,
            boolean includeDeleted
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "id"));

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Account> accountPage = accountRepository.searchAccounts(
                normalizedKeyword,
                role,
                status,
                includeDeleted,
                pageable
        );

        List<AccountDto> content = accountPage.getContent()
                .stream()
                .map(accountMapper::toDto)
                .toList();

        PageResponse<AccountDto> response = new PageResponse<>(
                content,
                safePage,
                safeSize,
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.isFirst(),
                accountPage.isLast()
        );
        response.setActiveCount(accountRepository.countByStatus(AccountStatus.ACTIVE));
        response.setSuspendedCount(accountRepository.countByStatus(AccountStatus.SUSPENDED));
        response.setDeletedCount(accountRepository.countByStatus(AccountStatus.DELETED));
        return response;
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.toDto(account);
    }

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request) {
        Account actor = getCurrentActor();
        AccountRole targetRole = request.getRole() != null ? request.getRole() : AccountRole.USER;
        enforceRoleHierarchy(actor, targetRole);

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String rawPassword = StringUtils.hasText(request.getPassword())
                ? request.getPassword().trim()
                : generateRandomPassword(10);

        Account account = new Account();
        account.setFullName(request.getFullName().trim());
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setRole(targetRole);
        account.setStatus(request.getStatus() != null ? request.getStatus() : AccountStatus.ACTIVE);
        account.setMustChangePassword(
                request.getMustChangePassword() != null
                        ? request.getMustChangePassword()
                        : !StringUtils.hasText(request.getPassword())
        );
        account.setValidUntil(request.getValidUntil());
        account.setCreatedBy(actor);

        Account saved = accountRepository.save(account);
        AccountDto dto = accountMapper.toDto(saved);
        dto.setGeneratedPassword(rawPassword);
        return dto;
    }

    @Transactional
    public AccountDto updateAccount(Long id, UpdateAccountRequest request) {
        Account actor = getCurrentActor();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        enforceRoleHierarchy(actor, account.getRole());

        if (request.getRole() != null) {
            enforceRoleHierarchy(actor, request.getRole());
            account.setRole(request.getRole());
        }

        if (StringUtils.hasText(request.getFullName())) {
            account.setFullName(request.getFullName().trim());
        }

        if (StringUtils.hasText(request.getEmail())) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(account.getEmail()) && accountRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            account.setEmail(newEmail);
        }

        if (StringUtils.hasText(request.getPassword())) {
            account.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        }

        if (request.getStatus() != null) {
            account.setStatus(request.getStatus());
            if (request.getStatus() == AccountStatus.DELETED || request.getStatus() == AccountStatus.SUSPENDED) {
                revokeAllRefreshTokens(account.getId());
            }
        }

        if (request.getMustChangePassword() != null) {
            account.setMustChangePassword(request.getMustChangePassword());
        }

        if (request.getValidUntil() != null) {
            account.setValidUntil(request.getValidUntil());
        }

        Account saved = accountRepository.save(account);
        return accountMapper.toDto(saved);
    }

    @Transactional
    public AccountDto softDeleteAccount(Long id) {
        Account actor = getCurrentActor();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (actor != null && actor.getId().equals(account.getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tự xóa tài khoản đang đăng nhập của chính mình");
        }

        enforceRoleHierarchy(actor, account.getRole());

        account.setStatus(AccountStatus.DELETED);
        Account saved = accountRepository.save(account);
        revokeAllRefreshTokens(account.getId());

        return accountMapper.toDto(saved);
    }

    @Transactional
    public ExcelImportResponse importAccountsFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng chọn file Excel (.xlsx hoặc .xls) để tải lên");
        }

        Account actor = getCurrentActor();
        ExcelImportResponse response = new ExcelImportResponse();
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            // Tự động nhận diện vị trí cột dựa vào dòng tiêu đề (nếu có)
            int emailCol = 0;
            int passCol = 1;
            int nameCol = 2;
            int roleCol = 3;
            int startRow = 0;

            Row firstRow = sheet.getRow(0);
            if (firstRow != null) {
                String c0 = getCellText(firstRow, 0, formatter).toLowerCase();
                String c1 = getCellText(firstRow, 1, formatter).toLowerCase();
                String c2 = getCellText(firstRow, 2, formatter).toLowerCase();
                String c3 = getCellText(firstRow, 3, formatter).toLowerCase();

                if (c0.contains("email") || c0.contains("gmail") || c0.contains("tên") || c0.contains("name") || c1.contains("email")) {
                    startRow = 1;
                    // Nếu cột 0 là tên và cột 1 là email
                    if ((c0.contains("tên") || c0.contains("name") || c0.contains("full_name")) && (c1.contains("email") || c1.contains("gmail"))) {
                        nameCol = 0;
                        emailCol = 1;
                        passCol = 2;
                        roleCol = 3;
                    } else {
                        emailCol = 0;
                        passCol = 1;
                        if (c2.contains("role") || c2.contains("quyền") || c2.contains("vai trò")) {
                            roleCol = 2;
                            nameCol = 3;
                        } else {
                            nameCol = 2;
                            roleCol = 3;
                        }
                    }
                }
            }

            for (int rowIdx = startRow; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }

                response.setTotalRows(response.getTotalRows() + 1);
                int displayRow = rowIdx + 1;

                try {
                    String rawEmail = getCellText(row, emailCol, formatter);
                    String rawPassword = getCellText(row, passCol, formatter);
                    String rawName = getCellText(row, nameCol, formatter);
                    String rawRole = getCellText(row, roleCol, formatter);

                    // Nếu người dùng truyền Role ở cột thứ 3 (nameCol) và không có cột thứ 4
                    if (isRoleName(rawName) && !StringUtils.hasText(rawRole)) {
                        rawRole = rawName;
                        rawName = "";
                    }

                    if (!StringUtils.hasText(rawEmail)) {
                        throw new IllegalArgumentException("Cột email/gmail không được để trống");
                    }

                    String normalizedEmail = rawEmail.trim().toLowerCase();
                    if (!normalizedEmail.contains("@")) {
                        throw new IllegalArgumentException("Email không hợp lệ: " + normalizedEmail);
                    }
                    if (accountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                        throw new IllegalArgumentException("Email '" + normalizedEmail + "' đã tồn tại trong DB");
                    }

                    AccountRole targetRole = AccountRole.USER;
                    if (StringUtils.hasText(rawRole)) {
                        try {
                            targetRole = AccountRole.valueOf(rawRole.trim().toUpperCase());
                        } catch (IllegalArgumentException ex) {
                            throw new IllegalArgumentException("Role không hợp lệ: " + rawRole);
                        }
                    }

                    if (actor != null && actor.getRole() == AccountRole.MANAGER
                            && !MANAGER_ALLOWED_ROLES.contains(targetRole)) {
                        throw new IllegalArgumentException(
                                "Manager chỉ được import các role: USER, CHECKER, APPROVER (không được import " + targetRole + ")"
                        );
                    }

                    boolean passwordProvided = StringUtils.hasText(rawPassword);
                    String plainPassword = passwordProvided ? rawPassword.trim() : generateRandomPassword(10);

                    String fullName = StringUtils.hasText(rawName)
                            ? rawName.trim()
                            : normalizedEmail.substring(0, normalizedEmail.indexOf('@'));

                    Account account = new Account();
                    account.setFullName(fullName);
                    account.setEmail(normalizedEmail);
                    account.setRole(targetRole);
                    account.setStatus(AccountStatus.ACTIVE);
                    account.setPasswordHash(passwordEncoder.encode(plainPassword));
                    account.setMustChangePassword(!passwordProvided);
                    account.setCreatedBy(actor);

                    Account saved = accountRepository.save(account);
                    AccountDto dto = accountMapper.toDto(saved);
                    dto.setGeneratedPassword(plainPassword);

                    response.getImportedAccounts().add(dto);
                    response.setSuccessCount(response.getSuccessCount() + 1);
                } catch (Exception rowEx) {
                    response.setFailedCount(response.getFailedCount() + 1);
                    response.getErrors().add("Dòng " + displayRow + ": " + rowEx.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new AppException(ErrorCode.EXCEL_PARSE_ERROR, "Lỗi đọc file Excel: " + ex.getMessage());
        }

        return response;
    }

    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Accounts_Import");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            String[] columns = {"email", "password", "full_name", "role"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            Row sample1 = sheet.createRow(1);
            sample1.createCell(0).setCellValue("user01@gmail.com");
            sample1.createCell(1).setCellValue("123456");
            sample1.createCell(2).setCellValue("Nguyễn Văn A");
            sample1.createCell(3).setCellValue("USER");

            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("user02@gmail.com");
            sample2.createCell(1).setCellValue("123456");
            sample2.createCell(2).setCellValue("Trần Thị B");
            sample2.createCell(3).setCellValue("CHECKER");

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể tạo file Excel mẫu");
        }
    }

    private boolean isRoleName(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            AccountRole.valueOf(value.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(SECURE_RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    private void enforceRoleHierarchy(Account actor, AccountRole targetRole) {
        if (actor != null && actor.getRole() == AccountRole.MANAGER) {
            if (!MANAGER_ALLOWED_ROLES.contains(targetRole)) {
                throw new AppException(
                        ErrorCode.FORBIDDEN,
                        "Tài khoản MANAGER chỉ được phép quản lý các tài khoản có role: USER, CHECKER, APPROVER"
                );
            }
        }
    }

    private Account getCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return accountRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }

    private void revokeAllRefreshTokens(Long accountId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByAccount_IdAndRevokedAtIsNull(accountId);
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int c = 0; c < 4; c++) {
            if (StringUtils.hasText(getCellText(row, c, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String getCellText(Row row, int colIdx, DataFormatter formatter) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }
}
