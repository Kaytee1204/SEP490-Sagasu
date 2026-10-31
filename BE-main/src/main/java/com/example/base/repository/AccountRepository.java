package com.example.base.repository;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("""
            select a from Account a where a.id = :id
              and a.role in (com.example.base.entity.AccountRole.USER,
                  com.example.base.entity.AccountRole.CHECKER)
              and a.status = com.example.base.entity.AccountStatus.ACTIVE
              and (a.validUntil is null or a.validUntil > CURRENT_TIMESTAMP)
              and (a.lockedUntil is null or a.lockedUntil <= CURRENT_TIMESTAMP)
            """)
    Optional<Account> findActiveAccountForPostCreation(@Param("id") Long id);

    Optional<Account> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Account> findByRole(AccountRole role);

    List<Account> findByStatus(AccountStatus status);

    long countByStatus(AccountStatus status);

    @Query("""
            SELECT a FROM Account a
            WHERE (:role IS NULL OR a.role = :role)
              AND (:status IS NULL OR a.status = :status)
              AND (
                   :keyword IS NULL OR :keyword = ''
                   OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(a.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<Account> searchAccounts(
            @Param("keyword") String keyword,
            @Param("role") AccountRole role,
            @Param("status") AccountStatus status,
            Pageable pageable
    );
}
