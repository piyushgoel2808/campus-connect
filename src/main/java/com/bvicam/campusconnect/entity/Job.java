package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String company;
    private String location;

    @Column(length = 1000) // Allow longer descriptions
    private String description;

    private String applyLink; // Email or URL

    @ManyToOne
    @JoinColumn(name = "posted_by_id")
    private User postedBy; // Links the job to the Alumnus who posted it

    private LocalDateTime postedAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "target_department_id")
    private Department targetDepartment;
}