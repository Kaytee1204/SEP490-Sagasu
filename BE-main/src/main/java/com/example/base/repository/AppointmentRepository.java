package com.example.base.repository;

import com.example.base.entity.Appointment;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findFirstByClaim_IdOrderByIdDesc(Long claimId);
    Optional<Appointment> findFirstByClaim_IdAndStatusOrderByIdDesc(Long claimId, String status);

    @Modifying
    @Query("update Appointment a set a.emailSentAt = :sentAt, a.version = a.version + 1 where a.id = :id and a.status = 'SCHEDULED'")
    void markEmailSent(@Param("id") long id, @Param("sentAt") OffsetDateTime sentAt);
}
