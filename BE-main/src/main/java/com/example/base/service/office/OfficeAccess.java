package com.example.base.service.office;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.AccountRepository;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component @RequiredArgsConstructor
public class OfficeAccess {
    private final AccountRepository accounts;
    public Account require(CustomUserDetails user, AccountRole... roles) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        Account actor = accounts.findById(user.getId()).orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        CustomUserDetails current = new CustomUserDetails(actor);
        if (actor.getStatus()!=AccountStatus.ACTIVE || !current.isAccountNonExpired() || !current.isAccountNonLocked()
            || (roles.length>0 && Arrays.stream(roles).noneMatch(role -> role==actor.getRole())))
            throw new AppException(ErrorCode.FORBIDDEN);
        return actor;
    }
    public static AppException bad(String message) { return new AppException(ErrorCode.BAD_REQUEST, message); }
}
