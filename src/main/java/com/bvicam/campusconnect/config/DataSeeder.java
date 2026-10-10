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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Startup database seeder that populates initial reference data and demo accounts.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Executes automatically once the Spring application context and JPA repositories are initialized.</li>
 *   <li>Uses idempotent existence checks ({@code existsByEmail}, {@code findByCode}) so restarting the
 *       server never creates duplicate records or overwrites existing user data.</li>
 *   <li>Seeds one user for each {@link Role} ({@code ADMIN}, {@code ALUMNI}, {@code STUDENT}), a sample
 *       {@link Job} posting, a sample wall {@link Post}, and default academic {@link Department} entries
 *       ({@code MCA} and {@code BJMC}).</li>
 * </ul>
 */
@Configuration
public class DataSeeder {

    /**
     * Registers a {@link CommandLineRunner} bean that seeds default users, sample content,
     * and academic departments on application startup if they do not already exist.
     *
     * @param userRepo        repository for querying and persisting {@link User} accounts
     * @param jobRepo         repository for persisting sample {@link Job} listings
     * @param postRepo        repository for persisting sample wall {@link Post} items
     * @param passwordEncoder BCrypt encoder used to hash plaintext seed passwords before saving
     * @param deptRepo        repository for querying and persisting academic {@link Department} records
     * @return the startup {@link CommandLineRunner} callback
     */
    @Bean
    CommandLineRunner initDatabase(UserRepository userRepo,
                                   JobRepository jobRepo,
                                   PostRepository postRepo,
                                   PasswordEncoder passwordEncoder,
                                   DepartmentRepository deptRepo) {
        return args -> {
            // -----------------------------------------------------------------
            // 1. Seed Default ADMIN Account (admin@bvicam.in / admin123)
            // -----------------------------------------------------------------
            if (!userRepo.existsByEmail("admin@bvicam.in")) {
                User admin = new User();
                admin.setName("Super Admin");
                admin.setEmail("admin@bvicam.in");
                admin.setPasswordHash(passwordEncoder.encode("admin123")); // Password: admin123
                admin.setRole(Role.ADMIN);
                userRepo.save(admin);
                System.out.println("✅ Created Admin: admin@bvicam.in / admin123");
            }

            // -----------------------------------------------------------------
            // 2. Seed Default ALUMNI Account & Sample Job Opportunity
            // -----------------------------------------------------------------
            if (!userRepo.existsByEmail("alumni@bvicam.in")) {
                User alumni = new User();
                alumni.setName("Amit Verma");
                alumni.setEmail("alumni@bvicam.in");
                alumni.setPasswordHash(passwordEncoder.encode("1234")); // Password: 1234
                alumni.setRole(Role.ALUMNI);
                alumni.setBatchYear(2020);
                alumni.setSkills("Java, Spring Boot, AWS");
                userRepo.save(alumni);
                System.out.println("✅ Created Alumni: alumni@bvicam.in / 1234");

                // Add a Dummy Job for this Alumni so the Jobs board is populated out-of-the-box
                Job job = new Job();
                job.setTitle("Junior Java Developer");
                job.setCompany("TCS");
                job.setLocation("Noida");
                job.setDescription("Looking for freshers with good Java knowledge.");
                job.setApplyLink("careers@tcs.com");
                job.setPostedBy(alumni);
                jobRepo.save(job);
            }

            // -----------------------------------------------------------------
            // 3. Seed Default STUDENT Account & Sample Wall Post
            // -----------------------------------------------------------------
            if (!userRepo.existsByEmail("student@bvicam.in")) {
                User student = new User();
                student.setName("Aadish Goel");
                student.setEmail("student@bvicam.in");
                student.setPasswordHash(passwordEncoder.encode("1234")); // Password: 1234
                student.setRole(Role.STUDENT);
                student.setEnrollmentNumber("0012024");
                userRepo.save(student);
                System.out.println("✅ Created Student: student@bvicam.in / 1234");

                // Add a Dummy Wall Post so the Campus Feed is populated out-of-the-box
                Post post = new Post();
                post.setContent("Just finished my minor project! Excited for the final exams.");
                post.setAuthor(student);
                post.setLikes(5);
                postRepo.save(post);
            }

            // -----------------------------------------------------------------
            // 4. Seed Default Academic Departments (MCA & BJMC)
            // -----------------------------------------------------------------
            if (deptRepo.findByCode("MCA").isEmpty()) {
                deptRepo.save(new Department("Master of Computer Applications", "MCA"));
                System.out.println("✅ Created Department: MCA");
            }

            // Check if BJMC exists, if not, create it
            if (deptRepo.findByCode("BJMC").isEmpty()) {
                deptRepo.save(new Department("Bachelors of Journalism", "BJMC"));
                System.out.println("✅ Created Department: BJMC");
            }
        };
    }
}