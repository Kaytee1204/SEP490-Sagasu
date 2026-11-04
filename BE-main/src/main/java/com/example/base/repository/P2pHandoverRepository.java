package com.example.base.repository;
import com.example.base.entity.P2pHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface P2pHandoverRepository extends JpaRepository<P2pHandover,Long> {
    Optional<P2pHandover> findByClaimId(long claim);
    boolean existsByClaimId(long claim);
    Optional<P2pHandover> findByRecordedByAndClientRequestId(long actor,UUID request);
}
