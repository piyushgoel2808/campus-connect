package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "private_messages")
public class PrivateMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String senderEmail;
    private String receiverEmail;
    private String content;
    private LocalDateTime timestamp = LocalDateTime.now();
}