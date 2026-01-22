package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;

    // 1. Get All Events (With RSVP Status)
    @GetMapping
    public List<Event> getAllEvents(Principal principal) {
        List<Event> events = eventRepository.findAll();

        if (principal != null) {
            User currentUser = userRepository.findByEmail(principal.getName()).orElse(null);
            if (currentUser != null) {
                // Calculate status for each event
                for (Event e : events) {
                    e.setAttending(e.getParticipants().contains(currentUser));
                    e.setParticipantCount(e.getParticipants().size());
                }
            }
        }
        return events;
    }

    // 2. Create Event
    @PostMapping
    public Event createEvent(@RequestBody Event event) {
        return eventRepository.save(event);
    }

    // 3. ✅ NEW: Edit Event
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event updatedData) {
        return eventRepository.findById(id).map(event -> {
            event.setTitle(updatedData.getTitle());
            event.setDescription(updatedData.getDescription());
            event.setLocation(updatedData.getLocation());
            event.setDateTime(updatedData.getDateTime());
            eventRepository.save(event);
            return ResponseEntity.ok("Event updated!");
        }).orElse(ResponseEntity.notFound().build());
    }

    // 4. ✅ NEW: Toggle RSVP (Join/Leave)
    @PostMapping("/{id}/rsvp")
    public ResponseEntity<?> toggleRsvp(@PathVariable Long id, Principal principal) {
        Event event = eventRepository.findById(id).orElseThrow();
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user); // Leave
        } else {
            event.getParticipants().add(user); // Join
        }

        eventRepository.save(event);
        return ResponseEntity.ok().build();
    }

    // 5. ✅ NEW: Get Participants List (Admin Only)
    @GetMapping("/{id}/participants")
    public ResponseEntity<?> getParticipants(@PathVariable Long id) {
        Event event = eventRepository.findById(id).orElseThrow();
        // Return simple list of maps to avoid password leaks
        List<String> names = event.getParticipants().stream()
                .map(u -> u.getName() + " (" + u.getEmail() + ")")
                .collect(Collectors.toList());
        return ResponseEntity.ok(names);
    }

    @DeleteMapping("/{id}")
    public void deleteEvent(@PathVariable Long id) {
        eventRepository.deleteById(id);
    }
}