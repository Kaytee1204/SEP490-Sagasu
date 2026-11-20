package com.example.base.repository;

import com.example.base.entity.Claim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {
    long countByClaimant_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(long accountId,
            java.time.OffsetDateTime start, java.time.OffsetDateTime end);

    @Query(value="select exists(select 1 from claim c join office_claim_verification v on v.claim_id=c.claim_id where c.post_id=:postId and c.claimant_id=:claimantId and v.result='FAILED')",nativeQuery=true)
    boolean hasFailedOfficeVerification(@Param("postId") long postId,@Param("claimantId") long claimantId);
    // DaiHV integration: always called after locking the post, matching P2P lock order.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.post.id=:postId order by c.id")
    java.util.List<Claim> findAllForOfficeTransfer(@Param("postId") long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.post.id=:postId and c.status='PENDING' order by c.id")
    java.util.List<Claim> findWaitingForReturn(@Param("postId") long postId);

    @EntityGraph(attributePaths = {"claimant"})
    @Query("select c from Claim c where c.post.id=:postId and c.id<>:excludeClaimId and c.status in :statuses order by c.id")
    java.util.List<Claim> findOtherActiveClaims(
            @Param("postId") long postId,
            @Param("excludeClaimId") long excludeClaimId,
            @Param("statuses") Collection<String> statuses
    );

    Optional<Claim> findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(long postId, String status);
    boolean existsByPost_Id(Long postId);
    boolean existsByPost_IdAndStatusIn(Long postId, Collection<String> statuses);
    boolean existsByPost_IdAndClaimant_IdAndStatusIn(Long postId, Long claimantId, Collection<String> statuses);
    Optional<Claim> findFirstByPost_IdAndClaimant_IdOrderByCreatedAtDescIdDesc(Long postId, Long claimantId);
    Optional<Claim> findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(Long postId, Long claimantId, String claimType);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.claimType='P2P' and c.claimant.id=:actorId and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by c.createdAt desc, c.id desc")
    Page<Claim> findMine(@Param("actorId") long actorId, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.claimType='P2P' and c.post.account.id=:finderId and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by case when c.status='PENDING' then 0 else 1 end, c.createdAt desc, c.id desc")
    Page<Claim> findReceived(@Param("finderId") long finderId, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.post.id=:postId and ((c.claimType='P2P' and c.post.account.id=:actorId) or (c.claimType='OFFICE' and :approver=true)) and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by c.createdAt desc, c.id desc")
    Page<Claim> findOnPost(@Param("postId") long postId, @Param("actorId") long actorId, @Param("approver") boolean approver, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.claimType='OFFICE' and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by c.createdAt desc, c.id desc")
    Page<Claim> findOffice(@Param("status") String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id=:id")
    Optional<Claim> findForUpdate(@Param("id") long id);

    @Query("select c.post.id from Claim c where c.id=:id")
    Optional<Long> findPostId(@Param("id") long id);
}
