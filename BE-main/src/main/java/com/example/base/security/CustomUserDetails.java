package com.example.base.security;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final AccountRole role;
    private final AccountStatus status;
    private final OffsetDateTime lockedUntil;
    private final OffsetDateTime validUntil;

    public CustomUserDetails(Account account) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.status = account.getStatus();
        this.lockedUntil = account.getLockedUntil();
        this.validUntil = account.getValidUntil();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public AccountRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return validUntil == null || validUntil.isAfter(OffsetDateTime.now());
    }

    @Override
    public boolean isAccountNonLocked() {
        if (status == AccountStatus.SUSPENDED) {
            return false;
        }
        return lockedUntil == null || lockedUntil.isBefore(OffsetDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.PENDING_ACTIVATION;
    }
}
