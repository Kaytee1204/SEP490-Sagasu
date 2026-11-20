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
    String MINE_FILTERS = """
            from report r
            left join p2p_claim_appeal a on a.report_id = r.report_id
            where r.reporter_id = :actor
              and (:status = 'ALL' or r.status = :status)
              and (:type = 'ALL'
                or (:type = 'CLAIM_APPEAL' and a.report_id is not null)
                or (:type = 'HANDOVER_ISSUE' and r.kind = 'HANDOVER_ISSUE' and a.report_id is null)
                or (r.kind = 'GENERAL' and (
                    (:type = 'POST' and r.post_id is not null)
                    or (:type = 'CLAIM' and r.claim_id is not null)
                    or (:type = 'MESSAGE' and r.message_id is not null)
                    or (:type = 'USER' and r.reported_account_id is not null))))
            """;

    @Query(value = "select r.* " + MINE_FILTERS + " order by r.created_at desc, r.report_id desc",
            countQuery = "select count(*) " + MINE_FILTERS, nativeQuery = true)
    Page<Report> findMine(@Param("actor") long actor, @Param("status") String status,
                          @Param("type") String type, Pageable page);
}
