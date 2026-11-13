package com.example.base.repository;
import com.example.base.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ReportRepository extends JpaRepository<Report,Long> {
    Optional<Report> findByReporterIdAndClientRequestId(long actor, UUID request);
    Page<Report> findByReporterIdOrderByCreatedAtDescIdDesc(long actor, Pageable page);
    Page<Report> findByReporterIdAndStatusOrderByCreatedAtDescIdDesc(long actor,String status,Pageable page);
    @Query("""
            select r from Report r where r.reporterId = :actor
              and (:status = 'ALL' or r.status = :status)
              and (:type = 'ALL'
                or (:type = 'HANDOVER_ISSUE' and r.kind = 'HANDOVER_ISSUE')
                or (r.kind = 'GENERAL' and (
                    (:type = 'POST' and r.postId is not null)
                    or (:type = 'CLAIM' and r.claimId is not null)
                    or (:type = 'MESSAGE' and r.messageId is not null)
                    or (:type = 'USER' and r.reportedAccountId is not null))))
            order by r.createdAt desc, r.id desc
            """)
    Page<Report> findMine(@Param("actor") long actor, @Param("status") String status,
                          @Param("type") String type, Pageable page);
}
