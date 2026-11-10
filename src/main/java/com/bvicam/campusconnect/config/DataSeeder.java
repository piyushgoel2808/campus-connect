package com.bvicam.campusconnect.config;

import com.bvicam.campusconnect.entity.Department;
import com.bvicam.campusconnect.entity.Job;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.DepartmentRepository;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Startup database seeder that populates initial reference data and demo accounts when enabled.
 * Set {@code app.seed-demo-data=false} in production to disable automatic demo account creation.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo,
                                   JobRepository jobRepo,
                                   PostRepository postRepo,
                                   PasswordEncoder passwordEncoder,
                                   DepartmentRepository deptRepo) {
        return args -> {
            if (!userRepo.existsByEmail("admin@bvicam.in")) {
                User admin = new User();
                admin.setName("Super Admin");
                admin.setEmail("admin@bvicam.in");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setPasswordChanged(true);
                admin.setRole(Role.ADMIN);
                userRepo.save(admin);
                log.info("Seeded default ADMIN account (admin@bvicam.in)");
            }

            if (!userRepo.existsByEmail("alumni@bvicam.in")) {
                User alumni = new User();
                alumni.setName("Amit Verma");
                alumni.setEmail("alumni@bvicam.in");
                alumni.setPasswordHash(passwordEncoder.encode("1234"));
                alumni.setPasswordChanged(true);
                alumni.setRole(Role.ALUMNI);
                alumni.setBatchYear(2020);
                alumni.setSkills("Java, Spring Boot, AWS");
                userRepo.save(alumni);
                log.info("Seeded default ALUMNI account (alumni@bvicam.in)");

                Job job = new Job();
                job.setTitle("Junior Java Developer");
                job.setCompany("TCS");
                job.setLocation("Noida");
                job.setDescription("Looking for freshers with good Java knowledge.");
                job.setApplyLink("careers@tcs.com");
                job.setPostedBy(alumni);
                jobRepo.save(job);
            }

            if (!userRepo.existsByEmail("student@bvicam.in")) {
                User student = new User();
                student.setName("Aadish Goel");
                student.setEmail("student@bvicam.in");
                student.setPasswordHash(passwordEncoder.encode("1234"));
                student.setPasswordChanged(true);
                student.setRole(Role.STUDENT);
                student.setEnrollmentNumber("0012024");
                userRepo.save(student);
                log.info("Seeded default STUDENT account (student@bvicam.in)");

                Post post = new Post();
                post.setContent("Just finished my minor project! Excited for the final exams.");
                post.setAuthor(student);
                post.setLikes(5);
                postRepo.save(post);
            }

            if (deptRepo.findByCode("MCA").isEmpty()) {
                deptRepo.save(new Department("Master of Computer Applications", "MCA"));
            }
            if (deptRepo.findByCode("BJMC").isEmpty()) {
                deptRepo.save(new Department("Bachelors of Journalism", "BJMC"));
            }
        };
    }
}
