package com.example.base.repository;

import com.example.base.entity.ItemReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ItemReceiptRepository extends JpaRepository<ItemReceipt, Long> {
    @Query("""
        select r.id from ItemReceipt r where r.recordedBy.id = :owner and r.source = 'CHECKER'
        and (locate(lower(:keyword), lower(r.title)) > 0 or locate(lower(:keyword), lower(r.receiptCode)) > 0
             or locate(lower(:keyword), lower(r.foundLocation)) > 0)
        and (:filter = 'ALL'
             or (:filter = 'DRAFT' and r.status = 'DRAFT')
             or (:filter = 'SUBMITTED' and r.status = 'SUBMITTED' and r.reviewStatus = 'PENDING')
             or (:filter = 'NEEDS_INFO' and r.reviewStatus = 'NEEDS_INFO')
             or (:filter = 'ACCEPTED' and r.reviewStatus = 'ACCEPTED')
             or (:filter = 'REJECTED' and (r.reviewStatus = 'REJECTED' or r.status = 'REJECTED')))
        order by r.recordedAt desc, r.id desc
        """)
    Page<Long> findMyIds(@Param("owner") Long owner, @Param("keyword") String keyword,
                         @Param("filter") String filter, Pageable pageable);

    @Query("select distinct r from ItemReceipt r join fetch r.category left join fetch r.storageLocation left join fetch r.images where r.id in :ids and r.recordedBy.id = :owner")
    List<ItemReceipt> findMyDetails(@Param("ids") List<Long> ids, @Param("owner") Long owner);

    @Query("select r from ItemReceipt r where r.id = :id and r.recordedBy.id = :owner and r.source = 'CHECKER'")
    Optional<ItemReceipt> findOwned(@Param("id") Long id, @Param("owner") Long owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ItemReceipt r where r.id = :id and r.recordedBy.id = :owner and r.source = 'CHECKER'")
    Optional<ItemReceipt> lockOwned(@Param("id") Long id, @Param("owner") Long owner);

    @Query("""
        select r.id from ItemReceipt r where r.source in ('CHECKER', 'WALK_IN') and r.status <> 'DRAFT'
        and (:source = 'ALL' or r.source = :source)
        and (:filter = 'ALL' or r.reviewStatus = :filter)
        and (locate(lower(:keyword), lower(r.title)) > 0 or locate(lower(:keyword), lower(r.receiptCode)) > 0
             or locate(lower(:keyword), lower(coalesce(r.recordedBy.fullName, ''))) > 0)
        order by coalesce(r.submittedAt, r.recordedAt) asc, r.id asc
        """)
    Page<Long> findQueueIds(@Param("keyword") String keyword, @Param("filter") String filter,
                            @Param("source") String source, Pageable pageable);

    @Query("select distinct r from ItemReceipt r join fetch r.category join fetch r.recordedBy left join fetch r.storageLocation left join fetch r.images where r.id in :ids")
    List<ItemReceipt> findQueueDetails(@Param("ids") List<Long> ids);

    @Query("select r from ItemReceipt r where r.id = :id and r.source in ('CHECKER', 'WALK_IN') and r.status <> 'DRAFT'")
    Optional<ItemReceipt> findForReview(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ItemReceipt r where r.id = :id and r.source = 'CHECKER' and r.status <> 'DRAFT'")
    Optional<ItemReceipt> lockForReview(@Param("id") Long id);
}
