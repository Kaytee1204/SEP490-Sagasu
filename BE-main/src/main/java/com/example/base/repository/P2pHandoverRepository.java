package com.example.base.repository;
import com.example.base.entity.P2pHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface P2pHandoverRepository extends JpaRepository<P2pHandover,Long> {
    @Query("select h from P2pHandover h where h.claimId=:claim and h.status<>'CANCELLED'")
    Optional<P2pHandover> findByClaimId(@Param("claim") long claim);
    @Query("select (count(h)>0) from P2pHandover h where h.claimId=:claim and h.status<>'CANCELLED'")
    boolean existsByClaimId(@Param("claim") long claim);
    Optional<P2pHandover> findByRecordedByAndClientRequestId(long actor,UUID request);
}
