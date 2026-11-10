package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for managing campus {@link Event} entities.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByDateTimeAfterOrderByDateTimeAsc(LocalDateTime now);

    List<Event> findTop5ByDateTimeAfterOrderByDateTimeAsc(LocalDateTime now);

    List<Event> findAllByOrderByDateTimeAsc();

    @Query("SELECT e FROM Event e JOIN e.participants p WHERE p.id = :userId")
    List<Event> findByParticipantId(@Param("userId") Long userId);
}
