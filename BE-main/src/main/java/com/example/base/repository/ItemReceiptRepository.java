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
        select r.id from ItemReceipt r
        where (:filter = 'ALL' or r.status = :filter)
        and (locate(lower(:keyword), lower(r.title)) > 0 or locate(lower(:keyword), lower(r.receiptCode)) > 0
             or locate(lower(:keyword), lower(r.foundLocation)) > 0)
        order by r.recordedAt desc, r.id desc
        """)
    Page<Long> findIntakeIds(@Param("keyword") String keyword, @Param("filter") String filter, Pageable pageable);

    @Query("select r from ItemReceipt r where r.id = :id")
    Optional<ItemReceipt> findIntake(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ItemReceipt r where r.id = :id")
    Optional<ItemReceipt> lockIntake(@Param("id") Long id);

    @Query("""
        select r from ItemReceipt r join fetch r.receivedBy left join fetch r.storageLocation
        where r.postId in :postIds and r.receivedAt is not null and r.reviewStatus = 'ACCEPTED'
        and r.status in ('IN_STORAGE', 'RETURNED')
        """)
    List<ItemReceipt> findPublicIntakes(@Param("postIds") List<Long> postIds);

    boolean existsByPostId(Long postId);

    @Query("select distinct r from ItemReceipt r join fetch r.category join fetch r.recordedBy left join fetch r.storageLocation left join fetch r.images where r.id in :ids")
    List<ItemReceipt> findQueueDetails(@Param("ids") List<Long> ids);

}
