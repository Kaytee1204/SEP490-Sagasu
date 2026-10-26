package com.example.base.repository;

import com.example.base.entity.LoginAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLog, Long> {

    List<LoginAuditLog> findByAccount_IdOrderByCreatedAtDesc(Long accountId);

    List<LoginAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
