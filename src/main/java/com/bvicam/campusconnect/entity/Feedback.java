package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "feedback")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int rating; // 1 to 5

    @Column(length = 1000)
    private String comments;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User submittedBy;

    private LocalDateTime submittedAt = LocalDateTime.now();
}