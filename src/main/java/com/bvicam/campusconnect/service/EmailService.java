package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for sending outbound emails via Spring's {@link JavaMailSender}.
 * <p>
 * Used by administrators (via {@code AdminController}) to send campus-wide broadcast
 * announcements asynchronously in the background without blocking HTTP responses.
 * </p>
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    /**
     * Asynchronously sends an email announcement to every registered user in the system.
     * <p>
     * Annotated with {@link Async} so that the caller receives an immediate HTTP response
     * while the SMTP dispatches execute on a background thread pool. Individual send
     * failures are caught and logged so one invalid address does not abort the entire broadcast.
     * </p>
     *
     * @param subject the email subject line
     * @param body    the main message body to include in the personalized email template
     */
    @Async // Run in background
    public void sendBroadcastEmail(String subject, String body) {
        // Step 1: Retrieve all registered users from the database
        List<User> allUsers = userRepository.findAll();

        System.out.println("📨 Starting Broadcast to " + allUsers.size() + " users...");

        // Step 2: Iterate through each user and send a personalized plain-text email
        for (User user : allUsers) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject(subject);
                message.setText("Dear " + user.getName() + ",\n\n" + body + "\n\nRegards,\nBVICAM Campus Connect");

                mailSender.send(message);
                System.out.println("✅ Sent to: " + user.getEmail());
            } catch (Exception e) {
                // Log failure for this recipient and continue broadcasting to remaining users
                System.out.println("❌ Failed to send to: " + user.getEmail());
            }
        }
        System.out.println("📨 Broadcast Complete.");
    }
}