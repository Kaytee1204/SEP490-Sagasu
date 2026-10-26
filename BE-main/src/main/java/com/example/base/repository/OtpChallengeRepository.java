package com.example.base.repository;

import com.example.base.entity.OtpChallenge;
import com.example.base.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
            Long accountId, OtpPurpose purpose);

    List<OtpChallenge> findByAccount_IdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(
            Long accountId, OtpPurpose purpose);
}
