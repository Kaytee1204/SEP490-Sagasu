package com.example.base.repository;

import com.example.base.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
  Optional<Post> findByIdAndAccount_IdAndDeletedAtIsNullAndWithdrawnAtIsNull(long id, long accountId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Post p where p.id = :id and p.account.id = :ownerId and p.deletedAt is null and p.withdrawnAt is null")
  Optional<Post> findOwnedForUpdate(@Param("id") long id, @Param("ownerId") long ownerId);

  // Page IDs first: applying pagination to a collection fetch would load all rows
  // in memory.
  @Query(value = """
      select p.id from Post p where p.moderationStatus = 'VISIBLE'
        and p.withdrawnAt is null and p.deletedAt is null
      order by p.createdAt desc, p.id desc
      """, countQuery = """
      select count(p) from Post p where p.moderationStatus = 'VISIBLE'
        and p.withdrawnAt is null and p.deletedAt is null
      """)
  Page<Long> findVisibleIds(Pageable pageable);

  @Query("""
      select distinct p from Post p join fetch p.category left join fetch p.images
      where p.id in :ids and p.moderationStatus = 'VISIBLE'
        and p.withdrawnAt is null and p.deletedAt is null
      """)
  List<Post> findVisibleByIds(@Param("ids") List<Long> ids);

  @Query("""
      select distinct p from Post p join fetch p.category left join fetch p.images
      where p.id = :id and p.moderationStatus = 'VISIBLE'
        and p.withdrawnAt is null and p.deletedAt is null
      """)
  Optional<Post> findVisibleDetail(@Param("id") long id);

    @Query("""
            select distinct p from Post p join fetch p.category left join fetch p.images
            where p.id = :id and p.account.id = :ownerId
              and p.withdrawnAt is null and p.deletedAt is null
            """)
    Optional<Post> findOwnDetail(@Param("id") long id, @Param("ownerId") long ownerId);

}
