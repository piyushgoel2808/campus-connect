package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Async // Run in background
    public void sendBroadcastEmail(String subject, String body) {
        List<User> allUsers = userRepository.findAll();

        System.out.println("📨 Starting Broadcast to " + allUsers.size() + " users...");

        for (User user : allUsers) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject(subject);
                message.setText("Dear " + user.getName() + ",\n\n" + body + "\n\nRegards,\nBVICAM Campus Connect");

                mailSender.send(message);
                System.out.println("✅ Sent to: " + user.getEmail());
            } catch (Exception e) {
                System.out.println("❌ Failed to send to: " + user.getEmail());
            }
        }
        System.out.println("📨 Broadcast Complete.");
    }
}