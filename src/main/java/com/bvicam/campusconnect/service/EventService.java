package com.bvicam.campusconnect.service;


import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <--- CRITICAL IMPORT

import java.util.List;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. Get All
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    // 2. Get One
    public Event getEventById(Long id) {
        return eventRepository.findById(id).orElse(null);
    }

    // 3. Save (Create or Update)
    @Transactional // <--- THIS ENSURES DATA IS COMMITTED TO DB
    public Event saveEvent(Event event) {
        return eventRepository.save(event);
    }

    // 4. Delete
    @Transactional
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    // 5. RSVP Logic
    @Transactional
    public void rsvpUser(Long eventId, String userEmail) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found"));
        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user); // Un-RSVP
        } else {
            event.getParticipants().add(user); // RSVP
        }

        eventRepository.save(event); // Explicitly save changes
    }
}