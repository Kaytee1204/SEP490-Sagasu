package com.example.base.repository;

import com.example.base.entity.SavedPost;
import com.example.base.entity.SavedPostId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPostId> {
    String VISIBLE_SAVED = """
            from SavedPost s join s.post p where s.id.accountId=:accountId
              and p.deletedAt is null and p.moderationStatus='VISIBLE'
            """;

    @Query(value = "select p.id " + VISIBLE_SAVED + " and (:postType='' or p.postType=:postType) order by s.createdAt desc, p.id desc",
            countQuery = "select count(s) " + VISIBLE_SAVED + " and (:postType='' or p.postType=:postType)")
    Page<Long> findVisiblePostIds(@Param("accountId") long accountId, @Param("postType") String postType, Pageable pageable);

    @Query("select p.id " + VISIBLE_SAVED)
    List<Long> findVisiblePostIds(@Param("accountId") long accountId);

    // Repeated saves, including concurrent requests, keep one row and the original saved date.
    @Modifying
    @Query(value = "insert into saved_post (account_id,post_id,created_at) values (:accountId,:postId,current_timestamp) on conflict (account_id,post_id) do nothing", nativeQuery = true)
    int saveIfAbsent(@Param("accountId") long accountId, @Param("postId") long postId);

    @Modifying
    @Query("delete from SavedPost s where s.id.accountId=:accountId and s.id.postId=:postId")
    int removeForAccount(@Param("accountId") long accountId, @Param("postId") long postId);
}
