package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for managing campus {@link Event} entities.
 * <p>
 * Supports standard CRUD operations as well as querying upcoming events for the
 * events dashboard and the Campus Bot assistant.
 * </p>
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Finds all events scheduled after the specified timestamp, sorted in ascending
     * chronological order (soonest upcoming event first).
     *
     * @param now the reference timestamp (typically {@code LocalDateTime.now()})
     * @return a list of upcoming {@link Event} entities ordered by {@code dateTime} ascending
     */
    // Find events happening after today
    List<Event> findByDateTimeAfterOrderByDateTimeAsc(java.time.LocalDateTime now);
}