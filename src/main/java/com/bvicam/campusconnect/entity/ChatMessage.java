package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The actual text message
    private String content;

    // When it was sent
    private LocalDateTime timestamp;

    // Who sent it? (Links to User table)
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    // Who received it? (Links to User table)
    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;
}