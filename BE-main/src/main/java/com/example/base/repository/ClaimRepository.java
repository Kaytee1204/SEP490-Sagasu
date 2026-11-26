package com.example.base.repository;

import com.example.base.entity.Claim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
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

    @Query(value = """
        select exists (
            select 1 from claim c
            join p2p_claim_appeal appeal on appeal.appellant_claim_id = c.claim_id
            join p2p_claim_approval approval on approval.approval_id = appeal.approval_id
            join report_decision d on d.report_id = appeal.report_id
            where c.post_id = :postId and c.claimant_id = :claimantId
              and (d.action = 'APPEAL_REJECT_CLAIM' or (approval.custody_mode='P2P' and d.action='APPEAL_KEEP'))
            union all
            select 1 from claim c
            join report r on r.claim_id=c.claim_id
            join report_decision d on d.report_id=r.report_id
            where c.post_id=:postId and c.claimant_id=:claimantId
              and d.after_state->>'claimResubmissionBlocked'='true'
        )
        """, nativeQuery = true)
    boolean hasResubmissionBlock(@Param("postId") long postId, @Param("claimantId") long claimantId);

    @Query(value = """
        select distinct on (appeal.appellant_claim_id) appeal.appellant_claim_id, d.action
        from p2p_claim_appeal appeal
        join report_decision d on d.report_id = appeal.report_id
        where appeal.appellant_claim_id in (:claimIds)
          and d.action in ('APPEAL_KEEP', 'APPEAL_REJECT_CLAIM', 'APPEAL_REQUIRE_REVIEW', 'APPEAL_WRONG_RECIPIENT')
        order by appeal.appellant_claim_id, d.decision_id desc
        """, nativeQuery = true)
    List<Object[]> findLatestDisputeOutcomes(@Param("claimIds") Collection<Long> claimIds);

    default String findLatestDisputeOutcome(Long claimId) {
        return findLatestDisputeOutcomes(List.of(claimId)).stream()
                .map(row -> (String) row[1]).findFirst().orElse(null);
    }

    @Query(value = """
        select d.conclusion from p2p_claim_approval a join p2p_claim_appeal e using(approval_id)
        join report_decision d using(report_id)
        join claim previous on previous.claim_id=a.approved_claim_id
        join claim candidate on candidate.post_id=previous.post_id and candidate.claimant_id=previous.claimant_id
        where candidate.claim_id=:claimId and a.custody_mode=candidate.claim_type
          and d.action='APPEAL_WRONG_RECIPIENT'
        order by d.decision_id desc limit 1
        """, nativeQuery = true)
    String lastWrongRecipientConclusion(@Param("claimId") long claimId);

    static boolean disputeEndsReview(String outcome) {
        return "APPEAL_KEEP".equals(outcome) || "APPEAL_REJECT_CLAIM".equals(outcome);
    }

    Optional<Claim> findFirstByPost_IdAndClaimant_IdOrderByCreatedAtDescIdDesc(Long postId, Long claimantId);
    Optional<Claim> findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(Long postId, Long claimantId, String claimType);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.claimType='P2P' and c.claimant.id=:actorId and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by c.createdAt desc, c.id desc")
    Page<Claim> findMine(@Param("actorId") long actorId, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.account", "claimant"})
    @Query("select c from Claim c where c.claimType='P2P' and c.post.account.id=:finderId and (:status='ALL' or c.status=:status or (:status='HISTORY' and c.status in ('COMPLETED','REJECTED','CANCELLED','CLOSED'))) order by case when c.status='PENDING' then 0 else 1 end, c.createdAt desc, c.id desc")
    Page<Claim> findReceived(@Param("finderId") long finderId, @Param("status") String status, Pageable pageable);

    @Query("""
        select c.id from Claim c
        where c.claimType = 'P2P' and c.post.account.id = :finderId and c.status = 'PENDING'
          and not exists (
              select approved.id from Claim approved
              where approved.post.id = c.post.id and approved.claimType = 'P2P' and approved.status = 'APPROVED'
          )
        """)
    List<Long> findPendingReviewIds(@Param("finderId") long finderId);

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
