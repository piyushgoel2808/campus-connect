package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.CreateEventRequest;
import com.bvicam.campusconnect.dto.EventDTO;
import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service layer for managing campus {@link Event} entities and participant RSVPs.
 */
@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<EventDTO> getAllEventDtos(String viewerEmail) {
        User currentUser = viewerEmail != null
                ? userRepository.findByEmail(viewerEmail).orElse(null)
                : null;

        return eventRepository.findAllByOrderByDateTimeAsc().stream()
                .map(event -> {
                    int count = event.getParticipants() != null ? event.getParticipants().size() : 0;
                    boolean isAttending = currentUser != null
                            && event.getParticipants() != null
                            && event.getParticipants().contains(currentUser);
                    return new EventDTO(
                            event.getId(),
                            event.getTitle(),
                            event.getDescription(),
                            event.getLocation(),
                            event.getDateTime(),
                            isAttending,
                            count
                    );
                })
                .toList();
    }

    @Transactional
    public EventDTO createEvent(CreateEventRequest request, String creatorEmail) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Event title is required.");
        }
        if (request.getDateTime() == null) {
            throw new IllegalArgumentException("Event date and time are required.");
        }

        Event event = new Event();
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        event.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);
        event.setDateTime(request.getDateTime());
        event.setOrganizerName(request.getOrganizerName());
        event.setParticipants(new HashSet<>());

        Event saved = eventRepository.save(event);

        notificationService.notifyAllUsersExceptAsync(
                creatorEmail,
                NotificationType.EVENT,
                "New event posted",
                saved.getTitle() + " is now available.",
                saved.getId()
        );

        return new EventDTO(saved.getId(), saved.getTitle(), saved.getDescription(),
                saved.getLocation(), saved.getDateTime(), false, 0);
    }

    @Transactional
    public EventDTO updateEvent(Long id, CreateEventRequest updatedData) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Event not found"));

        if (updatedData.getTitle() != null && !updatedData.getTitle().trim().isEmpty()) {
            event.setTitle(updatedData.getTitle().trim());
        }
        if (updatedData.getDescription() != null) {
            event.setDescription(updatedData.getDescription().trim());
        }
        if (updatedData.getLocation() != null) {
            event.setLocation(updatedData.getLocation().trim());
        }
        if (updatedData.getDateTime() != null) {
            event.setDateTime(updatedData.getDateTime());
        }

        Event saved = eventRepository.save(event);
        int count = saved.getParticipants() != null ? saved.getParticipants().size() : 0;
        return new EventDTO(saved.getId(), saved.getTitle(), saved.getDescription(),
                saved.getLocation(), saved.getDateTime(), false, count);
    }

    @Transactional
    public void rsvpUser(Long eventId, String userEmail) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (event.getParticipants().contains(user)) {
            event.getParticipants().remove(user);
        } else {
            event.getParticipants().add(user);
        }

        eventRepository.save(event);

        notificationService.createAndDispatch(
                user.getEmail(),
                NotificationType.EVENT,
                "RSVP updated",
                "Your RSVP for \"" + event.getTitle() + "\" has been updated.",
                event.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<UserProfileDto> getEventParticipants(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found"));
        return event.getParticipants().stream()
                .map(UserProfileDto::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new NoSuchElementException("Event not found");
        }
        eventRepository.deleteById(id);
    }
}
