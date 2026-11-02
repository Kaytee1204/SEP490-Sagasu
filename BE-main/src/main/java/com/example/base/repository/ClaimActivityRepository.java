package com.example.base.repository;

import com.example.base.entity.ClaimActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimActivityRepository extends JpaRepository<ClaimActivity, Long> {
    @EntityGraph(attributePaths = "actor")
    List<ClaimActivity> findByClaim_IdOrderByCreatedAtAscIdAsc(long claimId);
}
