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

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // =========================================================
    // 1. GET ALL EVENTS (Response uses DTO)
    // =========================================================
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
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Event event, Principal principal) {
        try {
            // Prevent NullPointer if participants list is missing in JSON
            if(event.getParticipants() == null) {
                event.setParticipants(new HashSet<>());
            }

            Event savedEvent = eventRepository.save(event);

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
    @PutMapping("/{id}")
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
    @PostMapping("/{id}/rsvp")
    public ResponseEntity<?> toggleRsvp(@PathVariable Long id, Principal principal) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new RuntimeException("Event not found"));
        User user = userRepository.findByEmail(principal.getName()).orElseThrow(() -> new RuntimeException("User not found"));

        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user); // Leave
        } else {
            event.getParticipants().add(user); // Join
        }

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
    @GetMapping("/{id}/participants")
    public ResponseEntity<Set<User>> getEventParticipants(@PathVariable Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return ResponseEntity.ok(event.getParticipants());
    }

    // =========================================================
    // 6. DELETE EVENT
    // =========================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        if (!eventRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        eventRepository.deleteById(id);
        return ResponseEntity.ok("Event deleted");
    }
}