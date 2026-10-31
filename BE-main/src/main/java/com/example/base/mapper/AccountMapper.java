package com.example.base.mapper;

import com.example.base.dto.account.AccountDto;
import com.example.base.entity.Account;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AccountMapper {

    public AccountDto toDto(Account account) {
        if (account == null) {
            return null;
        }
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        if (StringUtils.hasText(account.getFullName())) {
            String cleanedName = account.getFullName()
                    .replaceAll("(?i)\\s*\\((manager|admin|user|checker|approver)\\)\\s*$", "")
                    .trim();
            dto.setFullName(cleanedName);
        } else if (StringUtils.hasText(account.getEmail())) {
            int atIdx = account.getEmail().indexOf('@');
            dto.setFullName(atIdx > 0 ? account.getEmail().substring(0, atIdx) : account.getEmail());
        }
        dto.setEmail(account.getEmail());
        dto.setPhone(account.getPhone());
        dto.setRole(account.getRole());
        dto.setStatus(account.getStatus());
        dto.setMustChangePassword(account.isMustChangePassword());
        dto.setFailedLoginCount(account.getFailedLoginCount());
        dto.setLockedUntil(account.getLockedUntil());
        if (account.getCreatedBy() != null) {
            dto.setCreatedById(account.getCreatedBy().getId());
            dto.setCreatedByEmail(account.getCreatedBy().getEmail());
        }
        dto.setValidUntil(account.getValidUntil());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }
}
