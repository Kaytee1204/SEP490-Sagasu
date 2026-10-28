package com.example.base.repository;

import com.example.base.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query(value = """
            select p.id from Post p where p.createdBy.id=:authorId and p.deletedAt is null
              and (:filter='ALL' or p.moderationStatus=:filter)
            order by p.createdAt desc, p.id desc
            """, countQuery = """
            select count(p) from Post p where p.createdBy.id=:authorId and p.deletedAt is null
              and (:filter='ALL' or p.moderationStatus=:filter)
            """)
    Page<Long> findMyIds(@Param("authorId") long authorId, @Param("filter") String filter, Pageable pageable);

    @Query("select distinct p from Post p join fetch p.category left join fetch p.images where p.id in :ids and p.createdBy.id=:authorId and p.deletedAt is null")
    List<Post> findMyByIds(@Param("ids") List<Long> ids, @Param("authorId") long authorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id=:id and p.account.id=:ownerId and p.deletedAt is null")
    Optional<Post> findManagedForUpdate(@Param("id") long id, @Param("ownerId") long ownerId);

    @Query("select distinct p from Post p join fetch p.category left join fetch p.images where p.id=:id and p.account.id=:ownerId and p.deletedAt is null")
    Optional<Post> findManagedDetail(@Param("id") long id, @Param("ownerId") long ownerId);

    Optional<Post> findByIdAndAccount_IdAndDeletedAtIsNull(long id, long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id=:id and p.account.id=:ownerId and p.deletedAt is null")
    Optional<Post> findOwnedForUpdate(@Param("id") long id, @Param("ownerId") long ownerId);

    // Page IDs before joining images so each page counts posts, not image rows.
    @Query(value = """
            select p.id from Post p where p.moderationStatus='VISIBLE' and p.deletedAt is null
            order by p.createdAt desc, p.id desc
            """, countQuery = """
            select count(p) from Post p where p.moderationStatus='VISIBLE' and p.deletedAt is null
            """)
    Page<Long> findVisibleIds(Pageable pageable);

    @Query("""
            select distinct p from Post p join fetch p.category left join fetch p.images
            where p.id in :ids and p.moderationStatus='VISIBLE' and p.deletedAt is null
            """)
    List<Post> findVisibleByIds(@Param("ids") List<Long> ids);

    @Query("""
            select distinct p from Post p join fetch p.category left join fetch p.images
            where p.id=:id and p.moderationStatus='VISIBLE' and p.deletedAt is null
            """)
    Optional<Post> findVisibleDetail(@Param("id") long id);

    @Query("""
            select distinct p from Post p join fetch p.category left join fetch p.images
            where p.id=:id and p.account.id=:ownerId and p.deletedAt is null
            """)
    Optional<Post> findOwnDetail(@Param("id") long id, @Param("ownerId") long ownerId);
}