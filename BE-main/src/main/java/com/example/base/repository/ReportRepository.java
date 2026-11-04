package com.example.base.repository;
import com.example.base.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface ReportRepository extends JpaRepository<Report,Long> {
    Optional<Report> findByReporterIdAndClientRequestId(long actor, UUID request);
    Page<Report> findByReporterIdOrderByCreatedAtDescIdDesc(long actor, Pageable page);
    Page<Report> findByReporterIdAndStatusOrderByCreatedAtDescIdDesc(long actor,String status,Pageable page);
}
