package com.example.base.repository;

import com.example.base.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByAccount_IdAndRevokedAtIsNull(Long accountId);

    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
}
