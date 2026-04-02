package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // e.g., "Master of Computer Applications"

    @Column(unique = true, nullable = false)
    private String code; // e.g., "MCA", "BJMC"

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }
}