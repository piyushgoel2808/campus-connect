package com.bvicam.campusconnect.config;

import com.bvicam.campusconnect.entity.Job;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo,
                                   JobRepository jobRepo,
                                   PostRepository postRepo,
                                   PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Create ADMIN
            if (!userRepo.existsByEmail("admin@bvicam.in")) {
                User admin = new User();
                admin.setName("Super Admin");
                admin.setEmail("admin@bvicam.in");
                admin.setPasswordHash(passwordEncoder.encode("admin123")); // Password: admin123
                admin.setRole("ADMIN");
                userRepo.save(admin);
                System.out.println("✅ Created Admin: admin@bvicam.in / admin123");
            }

            // 2. Create ALUMNI
            if (!userRepo.existsByEmail("alumni@bvicam.in")) {
                User alumni = new User();
                alumni.setName("Amit Verma");
                alumni.setEmail("alumni@bvicam.in");
                alumni.setPasswordHash(passwordEncoder.encode("1234")); // Password: 1234
                alumni.setRole("ALUMNI");
                alumni.setBatchYear(2020);
                alumni.setSkills("Java, Spring Boot, AWS");
                userRepo.save(alumni);
                System.out.println("✅ Created Alumni: alumni@bvicam.in / 1234");

                // Add a Dummy Job for this Alumni
                Job job = new Job();
                job.setTitle("Junior Java Developer");
                job.setCompany("TCS");
                job.setLocation("Noida");
                job.setDescription("Looking for freshers with good Java knowledge.");
                job.setApplyLink("careers@tcs.com");
                job.setPostedBy(alumni);
                jobRepo.save(job);
            }

            // 3. Create STUDENT
            if (!userRepo.existsByEmail("student@bvicam.in")) {
                User student = new User();
                student.setName("Rahul Sharma");
                student.setEmail("student@bvicam.in");
                student.setPasswordHash(passwordEncoder.encode("1234")); // Password: 1234
                student.setRole("STUDENT");
                student.setEnrollmentNumber("0012024");
                userRepo.save(student);
                System.out.println("✅ Created Student: student@bvicam.in / 1234");

                // Add a Dummy Wall Post
                Post post = new Post();
                post.setContent("Just finished my minor project! Excited for the final exams.");
                post.setAuthor(student);
                post.setLikes(5);
                postRepo.save(post);
            }
        };
    }
}