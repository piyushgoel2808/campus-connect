package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for the interactive Campus Bot Assistant ({@code /api/bot}).
 * <p>
 * Answers natural-language student and alumni questions regarding placement statistics,
 * batchmate directories, admin contact information, syllabus resources, and live upcoming
 * campus events queried from the database.
 * </p>
 */
@RestController
@RequestMapping("/api/bot")
public class BotController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private com.bvicam.campusconnect.repository.EventRepository eventRepository;

    /**
     * Processes a user question and returns a formatted HTML/Markdown response from the Campus Bot.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/bot/ask}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> JSON object containing {@code {"question": "..."}}</li>
     *   <li><b>Response:</b> JSON object containing {@code {"answer": "..."}}</li>
     * </ul>
     *
     * @param payload map containing the user's {@code "question"} string
     * @return a single-entry map with key {@code "answer"} containing the bot's reply
     */
    @PostMapping("/ask")
    public Map<String, String> askBot(@RequestBody Map<String, String> payload) {
        // Step 1: Normalize question text to lowercase and initialize fallback reply
        String question = payload.get("question").toLowerCase();
        String answer = "I'm sorry, I didn't understand that. Try asking about 'batch', 'placement', or 'events'.";

        // RULE 1: Placement Stats
        if (question.contains("placement") || question.contains("stats")) {
            answer = "📈 **Placement Stats 2024:**<br>" +
                    "• Highest Package: 12 LPA (Microsoft)<br>" +
                    "• Average Package: 6.5 LPA<br>" +
                    "• Top Recruiters: TCS, Nagarro, Knoldus";
        }
        // RULE 2: Batch Queries (e.g., "Who is in 2024 batch?")
        else if (question.contains("batch") || question.contains("class of")) {
            // Extract Year (Simple parsing)
            String yearStr = question.replaceAll("[^0-9]", "");
            if (!yearStr.isEmpty()) {
                int year = Integer.parseInt(yearStr);
                List<User> students = userRepository.findAll().stream()
                        .filter(u -> u.getBatchYear() != null && u.getBatchYear() == year)
                        .collect(Collectors.toList());

                if (students.isEmpty()) {
                    answer = "I couldn't find any students for the batch of " + year + ".";
                } else {
                    String names = students.stream().map(User::getName).collect(Collectors.joining(", "));
                    answer = "👥 **Batch " + year + " Students:**<br>" + names;
                }
            } else {
                answer = "Which batch year are you looking for? (e.g., '2024 batch')";
            }
        }
        // RULE 3: Contact Info
        else if (question.contains("contact") || question.contains("email")) {
            answer = "📧 You can reach the admin office at **admin@bvicam.in**.";
        }
        // RULE 4: Syllabus/Curriculum
        else if (question.contains("syllabus") || question.contains("subjects")) {
            answer = "📚 **MCA Syllabus:** You can download the latest syllabus from the Dashboard > Resources section.";
        }
        // RULE 5: Live Upcoming Campus Events from Database
        else if (question.contains("event") || question.contains("workshop") || question.contains("seminar")) {
            List<com.bvicam.campusconnect.entity.Event> events = eventRepository.findByDateTimeAfterOrderByDateTimeAsc(java.time.LocalDateTime.now());
            if (events.isEmpty()) {
                answer = "📅 There are no upcoming events scheduled at the moment.";
            } else {
                StringBuilder sb = new StringBuilder("📅 **Upcoming Events:**<br>");
                for (com.bvicam.campusconnect.entity.Event e : events) {
                    sb.append("• **").append(e.getTitle()).append("** at ").append(e.getLocation()).append("<br>");
                }
                answer = sb.toString();
            }
        }
        return Map.of("answer", answer);
    }
}