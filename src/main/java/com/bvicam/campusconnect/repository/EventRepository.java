package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    // Find events happening after today
    List<Event> findByDateTimeAfterOrderByDateTimeAsc(java.time.LocalDateTime now);
}