package com.example.base.repository;
import com.example.base.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findFirstByClaim_IdAndStatusOrderByIdDesc(Long claimId, String status);
}
