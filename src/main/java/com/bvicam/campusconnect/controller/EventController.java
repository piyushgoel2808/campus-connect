package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.EventDTO;
import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REST Controller for Campus Events Management ({@code /api/events}).
 * <p>
 * Provides endpoints for listing all events with personalized RSVP status ({@link EventDTO}),
 * creating and updating events, toggling user attendance (RSVP), viewing event participants,
 * and deleting events.
 * </p>
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // =========================================================
    // 1. GET ALL EVENTS (Response uses DTO)
    // =========================================================

    /**
     * Retrieves all campus events enriched with the total participant count and whether
     * the currently authenticated user is attending each event.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/events}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of {@link EventDTO} objects</li>
     * </ul>
     *
     * @param principal the currently authenticated user's security principal
     * @return a {@link ResponseEntity} containing the list of {@link EventDTO} representations
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<EventDTO>> getAllEvents(Principal principal) {
        // A. Get current user safely
        User currentUser = null;
        if (principal != null) {
            currentUser = userRepository.findByEmail(principal.getName()).orElse(null);
        }

        // B. Get all raw events from DB
        List<Event> events = eventRepository.findAll();
        List<EventDTO> dtos = new ArrayList<>();

        // C. Convert Entity -> DTO
        for (Event event : events) {
            boolean isAttending = false;
            int count = 0;

            // Safe check for null participants
            if (event.getParticipants() != null) {
                count = event.getParticipants().size(); // Calculate Count

                if (currentUser != null) {
                    isAttending = event.getParticipants().contains(currentUser); // Calculate Boolean
                }
            }

            // Add to list using the DTO constructor
            dtos.add(new EventDTO(
                    event.getId(),
                    event.getTitle(),
                    event.getDescription(),
                    event.getLocation(),
                    event.getDateTime(),
                    isAttending,
                    count
            ));
        }

        return ResponseEntity.ok(dtos);
    }

    // =========================================================
    // 2. CREATE EVENT (Request uses Entity)
    // =========================================================

    /**
     * Creates a new campus event and dispatches real-time in-app notifications to all other users.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/events}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@link Event} JSON body ({@code title}, {@code description}, {@code location}, {@code dateTime})</li>
     *   <li><b>Response:</b> {@code 200 OK} with saved {@link Event}, or {@code 400 Bad Request} on error</li>
     * </ul>
     *
     * @param event     the {@link Event} entity to create
     * @param principal the authenticated creator's principal
     * @return a {@link ResponseEntity} containing the created event or an error message
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createEvent(@RequestBody Event event, Principal principal) {
        try {
            // Prevent NullPointer if participants list is missing in JSON
            if(event.getParticipants() == null) {
                event.setParticipants(new HashSet<>());
            }

            // Persist the new event to MySQL
            Event savedEvent = eventRepository.save(event);

            // Notify all registered users (except the creator) about the new event
            String creatorEmail = principal != null ? principal.getName() : null;
            for (User user : userRepository.findAll()) {
                if (creatorEmail != null && creatorEmail.equalsIgnoreCase(user.getEmail())) {
                    continue;
                }
                notificationService.createAndDispatch(
                        user.getEmail(),
                        NotificationType.EVENT,
                        "New event posted",
                        savedEvent.getTitle() + " is now available.",
                        savedEvent.getId()
                );
            }

            return ResponseEntity.ok(savedEvent);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error creating event: " + e.getMessage());
        }
    }

    // =========================================================
    // 3. UPDATE EVENT (Request uses Entity)
    // =========================================================

    /**
     * Updates an existing campus event's title, description, location, or scheduled date/time.
     * Only non-null fields in the request payload are applied.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/events/{id}}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the event to update</li>
     *   <li><b>Request Payload:</b> {@link Event} JSON object with updated fields</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id          the ID of the event to update
     * @param updatedData the partial or full {@link Event} payload
     * @return a {@link ResponseEntity} confirming the update or 404 if not found
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event updatedData) {
        return eventRepository.findById(id).map(event -> {
            // Only update fields that are NOT null (Prevents accidental data loss)
            if (updatedData.getTitle() != null) event.setTitle(updatedData.getTitle());
            if (updatedData.getDescription() != null) event.setDescription(updatedData.getDescription());
            if (updatedData.getLocation() != null) event.setLocation(updatedData.getLocation());

            // This works because Event.java now has @JsonFormat
            if (updatedData.getDateTime() != null) event.setDateTime(updatedData.getDateTime());

            eventRepository.save(event);
            return ResponseEntity.ok("Event updated successfully!");
        }).orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // 4. RSVP LOGIC
    // =========================================================

    /**
     * Toggles the authenticated user's RSVP attendance status for the specified event
     * and dispatches an RSVP confirmation notification.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/events/{id}/rsvp}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the event</li>
     *   <li><b>Response:</b> {@code 200 OK} (empty body)</li>
     * </ul>
     *
     * @param id        the ID of the event to join or leave
     * @param principal the authenticated user's principal
     * @return an empty {@code 200 OK} {@link ResponseEntity}
     */
    @PostMapping("/{id}/rsvp")
    @Transactional
    public ResponseEntity<?> toggleRsvp(@PathVariable Long id, Principal principal) {
        // Step 1: Fetch the event and the authenticated user
        Event event = eventRepository.findById(id).orElseThrow(() -> new RuntimeException("Event not found"));
        User user = userRepository.findByEmail(principal.getName()).orElseThrow(() -> new RuntimeException("User not found"));

        // Step 2: Toggle the user's membership in the event's participants set
        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user); // Leave
        } else {
            event.getParticipants().add(user); // Join
        }

        // Step 3: Persist updated attendance and send confirmation notification
        eventRepository.save(event);

        notificationService.createAndDispatch(
            user.getEmail(),
            NotificationType.EVENT,
            "RSVP updated",
            "Your RSVP for \"" + event.getTitle() + "\" has been updated.",
            event.getId()
        );

        return ResponseEntity.ok().build();
    }

    // =========================================================
    // 5. VIEW PARTICIPANTS
    // =========================================================

    /**
     * Retrieves the set of users who have RSVP'd to a specific event.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/events/{id}/participants}</li>
     *   <li><b>Access Role:</b> Authenticated User / Admin</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the event</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON set of participating {@link User} entities</li>
     * </ul>
     *
     * @param id the ID of the event
     * @return a {@link ResponseEntity} containing the set of participants
     */
    @GetMapping("/{id}/participants")
    @Transactional(readOnly = true)
    public ResponseEntity<Set<User>> getEventParticipants(@PathVariable Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return ResponseEntity.ok(new HashSet<>(event.getParticipants()));
    }

    // =========================================================
    // 6. DELETE EVENT
    // =========================================================

    /**
     * Deletes a campus event by its ID.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/events/{id}}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the event to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id the ID of the event to delete
     * @return a {@link ResponseEntity} confirming deletion or 404 if not found
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        if (!eventRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        eventRepository.deleteById(id);
        return ResponseEntity.ok("Event deleted");
    }
}