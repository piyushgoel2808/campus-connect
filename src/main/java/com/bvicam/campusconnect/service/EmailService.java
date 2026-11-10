package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for sending outbound broadcast emails via Spring's {@link JavaMailSender}.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Async
    public void sendBroadcastEmail(String subject, String body) {
        if (mailUsername == null || mailUsername.isBlank() || mailUsername.contains("your-email@")) {
            log.warn("Skipping email broadcast '{}': SMTP credentials are not configured.", subject);
            return;
        }

        List<User> allUsers = userRepository.findAll();
        log.info("Starting email broadcast '{}' to {} users", subject, allUsers.size());

        int sent = 0;
        int failed = 0;
        for (User user : allUsers) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject(subject);
                message.setText("Dear " + user.getName() + ",\n\n" + body + "\n\nRegards,\nBVICAM Campus Connect");
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to send broadcast email to {}: {}", user.getEmail(), e.getMessage());
            }
        }
        log.info("Broadcast '{}' finished: sent={}, failed={}", subject, sent, failed);
    }
}
