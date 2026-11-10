package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.CreateEventRequest;
import com.bvicam.campusconnect.dto.EventDTO;
import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Campus Events Management ({@code /api/events}).
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents(Principal principal) {
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(eventService.getAllEventDtos(email));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventDTO> createEvent(@RequestBody CreateEventRequest request, Principal principal) {
        String creatorEmail = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(eventService.createEvent(request, creatorEmail));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody CreateEventRequest updatedData) {
        eventService.updateEvent(id, updatedData);
        return ResponseEntity.ok(Map.of("message", "Event updated successfully!"));
    }

    @PostMapping("/{id}/rsvp")
    public ResponseEntity<?> toggleRsvp(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        eventService.rsvpUser(id, principal.getName());
        return ResponseEntity.ok(Map.of("message", "RSVP updated"));
    }

    @GetMapping("/{id}/participants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileDto>> getEventParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventParticipants(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(Map.of("message", "Event deleted"));
    }
}
