package com.bvicam.campusconnect.service;


import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <--- CRITICAL IMPORT

import java.util.List;

/**
 * Service layer for managing campus {@link Event} entities and participant RSVPs.
 * <p>
 * Encapsulates transactional business logic for retrieving, saving, deleting,
 * and toggling user attendance on campus events.
 * </p>
 */
@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    // =========================================================
    // EVENT RETRIEVAL & PERSISTENCE
    // =========================================================

    /**
     * Retrieves all campus events from the database.
     *
     * @return a list of all {@link Event} entities
     */
    // 1. Get All
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    /**
     * Retrieves a single event by its unique identifier.
     *
     * @param id the ID of the event to look up
     * @return the matching {@link Event}, or {@code null} if no event exists with that ID
     */
    // 2. Get One
    public Event getEventById(Long id) {
        return eventRepository.findById(id).orElse(null);
    }

    /**
     * Creates a new event or updates an existing event within a database transaction.
     *
     * @param event the {@link Event} entity to persist
     * @return the saved {@link Event} entity
     */
    // 3. Save (Create or Update)
    @Transactional // <--- THIS ENSURES DATA IS COMMITTED TO DB
    public Event saveEvent(Event event) {
        return eventRepository.save(event);
    }

    /**
     * Deletes an event by its unique identifier within a database transaction.
     *
     * @param id the ID of the event to delete
     */
    // 4. Delete
    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    // =========================================================
    // PARTICIPANT RSVP MANAGEMENT
    // =========================================================

    /**
     * Toggles a user's RSVP status for a specific event.
     * <p>
     * If the user is already in the event's {@code participants} set, they are removed (Un-RSVP);
     * otherwise, they are added (RSVP).
     * </p>
     *
     * @param eventId   the ID of the event
     * @param userEmail the email address of the user toggling their RSVP
     * @throws RuntimeException if either the event or user cannot be found
     */
    // 5. RSVP Logic
    @Transactional
    public void rsvpUser(Long eventId, String userEmail) {
        // Step 1: Load the target event and user (or fail if either is missing)
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found"));
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

        // Step 2: Toggle attendance membership in the Many-to-Many participants set
        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user); // Un-RSVP
        } else {
            event.getParticipants().add(user); // RSVP
        }

        // Step 3: Persist updated join-table associations
        eventRepository.save(event); // Explicitly save changes
    }
}