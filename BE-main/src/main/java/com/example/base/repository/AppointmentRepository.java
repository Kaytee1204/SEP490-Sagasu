package com.example.base.repository;
import com.example.base.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByResponseTokenHash(String hash);
    Optional<Appointment> findFirstByClaim_IdOrderByIdDesc(Long claimId);
    @org.springframework.data.jpa.repository.Query("select a.claim.id from Appointment a where a.claim.claimType = 'OFFICE' and a.status = 'SCHEDULED' and a.responseStatus = 'PENDING' and a.responseDeadline <= :now order by a.responseDeadline")
    java.util.List<Long> findExpiredOfficeClaims(@org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now, org.springframework.data.domain.Pageable page);
    Optional<Appointment> findFirstByClaim_IdAndStatusOrderByIdDesc(Long claimId, String status);
}
