package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a persisted 1-on-1 direct message between two users, indexed by their email addresses.
 */
@Entity
@Data
@Table(name = "private_messages", indexes = {
        @Index(name = "idx_pm_sender_time", columnList = "sender_email, timestamp"),
        @Index(name = "idx_pm_receiver_time", columnList = "receiver_email, timestamp")
})
public class PrivateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderEmail;

    @Column(nullable = false)
    private String receiverEmail;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}
