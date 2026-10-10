package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.Department;
import com.bvicam.campusconnect.entity.Event;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.repository.CommentRepository;
import com.bvicam.campusconnect.repository.DepartmentRepository;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.FeedbackRepository;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Administrative Operations ({@code /api/admin}).
 * <p>
 * Provides endpoints for user lifecycle management (CRUD, password reset, moderation unflagging),
 * campus-wide email broadcasts, post moderation, and bulk user onboarding via Excel ({@code .xlsx})
 * or CSV ({@code .csv}) file uploads. Intended for users with the {@link Role#ADMIN} role.
 * </p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PostRepository postRepository;
    private final DepartmentRepository departmentRepository;
    private final CommentRepository commentRepository;
    private final JobRepository jobRepository;
    private final FeedbackRepository feedbackRepository;
    private final EventRepository eventRepository;

    /**
     * Constructs the {@code AdminController} with all required repositories and services injected.
     */
    @Autowired
    public AdminController(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           PostRepository postRepository,
                           DepartmentRepository departmentRepository,
                           CommentRepository commentRepository,
                           JobRepository jobRepository,
                           FeedbackRepository feedbackRepository,
                           EventRepository eventRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.postRepository = postRepository;
        this.departmentRepository = departmentRepository;
        this.commentRepository = commentRepository;
        this.jobRepository = jobRepository;
        this.feedbackRepository = feedbackRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Resolves a department short code to an existing {@link Department} entity,
     * normalizing legacy aliases (such as {@code "BA(JMC)"} to {@code "BJMC"}) and
     * auto-creating the department if it does not yet exist.
     *
     * @param code the department code string
     * @return the resolved or newly created {@link Department}, or {@code null} if the code is blank
     */
    private Department resolveDepartment(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        String normalized = code.trim();
        if ("BA(JMC)".equalsIgnoreCase(normalized)) {
            normalized = "BJMC";
        }
        final String finalCode = normalized;
        return departmentRepository.findByCode(finalCode)
                .orElseGet(() -> departmentRepository.save(new Department(finalCode, finalCode)));
    }

    // =========================================================
    // 1. USER MANAGEMENT (CRUD)
    // =========================================================

    /**
     * Retrieves all registered user accounts in the system.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/admin/users}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of {@link User} entities</li>
     * </ul>
     *
     * @return a {@link ResponseEntity} containing the list of all users
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * Creates a new user account (Student, Alumni, or Admin) from the Admin Dashboard.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/admin/users}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@link RegisterRequest} JSON body</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, {@code 400 Bad Request} if email is missing/duplicate</li>
     * </ul>
     *
     * @param request the registration details for the new user
     * @return a {@link ResponseEntity} with a confirmation or error message
     */
    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        try {
            // Step 1: Validate that a non-empty, unique email address was provided
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Error: Email is required!");
            }
            if (userRepository.existsByEmail(request.getEmail().trim())) {
                return ResponseEntity.badRequest().body("Error: Email already exists!");
            }

            // Step 2: Populate core identity and academic fields
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail().trim());
            user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
            user.setEnrollmentNumber(request.getEnrollmentNumber());
            user.setBatchYear(request.getBatchYear());

            // Step 3: Resolve and link the academic department if a departmentCode was supplied
            if (request.getDepartmentCode() != null && !request.getDepartmentCode().trim().isEmpty()) {
                user.setDepartment(resolveDepartment(request.getDepartmentCode()));
            }

            // Step 4: Set Professional Profile Fields
            user.setHeadline(request.getHeadline());
            user.setCurrentCompany(request.getCurrentCompany());
            user.setDesignation(request.getDesignation());
            user.setSkills(request.getSkills());
            user.setGithubUrl(request.getGithubUrl());
            user.setLinkedinUrl(request.getLinkedinUrl());
            user.setPastExperience(request.getPastExperience());

            // Step 5: Hash the provided password (or default to "Bvicam@2025") and mark passwordChanged = false
            String rawPass = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                    ? request.getPassword().trim()
                    : "Bvicam@2025";
            user.setPasswordHash(passwordEncoder.encode(rawPass));
            user.setPasswordChanged(false);

            // Step 6: Save the new user entity
            userRepository.save(user);
            return ResponseEntity.ok("✅ User created successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating user: " + e.getMessage());
        }
    }

    /**
     * Deletes a user account and cleans up all associated foreign-key records
     * (event RSVPs, comments, posts, jobs, and feedback).
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/admin/delete-user/{id}}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the user to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, or {@code 404 Not Found} if user does not exist</li>
     * </ul>
     *
     * @param id the ID of the user to delete
     * @return a {@link ResponseEntity} indicating success or not found
     */
    @DeleteMapping("/delete-user/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        // Step 1: Verify the user exists
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        // Step 2: Remove user from event participants join table to avoid FK constraint violations
        for (Event event : eventRepository.findAll()) {
            if (event.getParticipants() != null && event.getParticipants().remove(user)) {
                eventRepository.save(event);
            }
        }

        // Step 3: Remove comments, posts, jobs, and feedback authored by this user
        commentRepository.findAll().stream()
                .filter(c -> c.getAuthor() != null && id.equals(c.getAuthor().getId()))
                .forEach(commentRepository::delete);
        postRepository.findAll().stream()
                .filter(p -> p.getAuthor() != null && id.equals(p.getAuthor().getId()))
                .forEach(postRepository::delete);
        jobRepository.findAll().stream()
                .filter(j -> j.getPostedBy() != null && id.equals(j.getPostedBy().getId()))
                .forEach(jobRepository::delete);
        feedbackRepository.findAll().stream()
                .filter(f -> f.getSubmittedBy() != null && id.equals(f.getSubmittedBy().getId()))
                .forEach(feedbackRepository::delete);

        // Step 4: Delete the user record itself
        userRepository.delete(user);
        return ResponseEntity.ok("✅ User deleted successfully.");
    }

    /**
     * Updates an existing user's academic, role, and professional profile details.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/admin/users/{id}}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the user to update</li>
     *   <li><b>Request Payload:</b> {@link RegisterRequest} JSON body with updated fields</li>
     *   <li><b>Response:</b> {@code 200 OK} with confirmation message</li>
     * </ul>
     *
     * @param id          the ID of the user to update
     * @param updatedData the updated user fields
     * @return a {@link ResponseEntity} confirming the update
     */
    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody RegisterRequest updatedData) {
        // Step 1: Retrieve existing user by ID
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        // Step 2: Apply updated identity, academic, and professional fields
        if (updatedData.getName() != null) user.setName(updatedData.getName());
        if (updatedData.getEmail() != null) user.setEmail(updatedData.getEmail().trim());
        if (updatedData.getRole() != null) user.setRole(updatedData.getRole());
        user.setBatchYear(updatedData.getBatchYear());
        user.setEnrollmentNumber(updatedData.getEnrollmentNumber());
        user.setCurrentCompany(updatedData.getCurrentCompany());
        user.setDesignation(updatedData.getDesignation());
        user.setHeadline(updatedData.getHeadline());
        user.setSkills(updatedData.getSkills());
        user.setGithubUrl(updatedData.getGithubUrl());
        user.setLinkedinUrl(updatedData.getLinkedinUrl());
        user.setPastExperience(updatedData.getPastExperience());

        // Step 3: Update department association if provided
        if (updatedData.getDepartmentCode() != null && !updatedData.getDepartmentCode().trim().isEmpty()) {
            user.setDepartment(resolveDepartment(updatedData.getDepartmentCode()));
        }

        // Step 4: Persist changes
        userRepository.save(user);
        return ResponseEntity.ok("✅ User updated successfully.");
    }

    /**
     * Clears moderation flags ({@code isFlagged = false}, {@code flagCount = 0}) on a user account.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/admin/users/{id}/unflag}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the flagged user</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON confirmation, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id the ID of the user to unflag
     * @return a {@link ResponseEntity} confirming the flags were cleared
     */
    @PostMapping("/users/{id}/unflag")
    @Transactional
    public ResponseEntity<?> unflagUser(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            user.setIsFlagged(false);
            user.setFlagCount(0);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "User flags successfully cleared."));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found")));
    }

    /**
     * Resets a user's password back to the default temporary password ({@code "Bvicam@2025"}).
     * <ul>
     *   <li><b>HTTP Route:</b> {@code PUT /api/admin/users/{id}/reset-password}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the user whose password should be reset</li>
     *   <li><b>Response:</b> {@code 200 OK} with confirmation message</li>
     * </ul>
     *
     * @param id the ID of the user
     * @return a {@link ResponseEntity} confirming the password reset
     */
    @PutMapping("/users/{id}/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode("Bvicam@2025"));
        user.setPasswordChanged(false);
        userRepository.save(user);
        return ResponseEntity.ok("✅ Password reset to 'Bvicam@2025'");
    }

    // =========================================================
    // 2. MODERATION & BROADCAST (Restored from Version 1)
    // =========================================================

    /**
     * Triggers an asynchronous broadcast email to all registered users.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/admin/broadcast}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Request Payload:</b> JSON object containing {@code "subject"} and {@code "body"}</li>
     *   <li><b>Response:</b> {@code 200 OK} when background dispatch starts, or {@code 400 Bad Request}</li>
     * </ul>
     *
     * @param payload map containing {@code subject} and {@code body} strings
     * @return a {@link ResponseEntity} acknowledging the background email broadcast
     */
    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody Map<String, String> payload) {
        String subject = payload.get("subject");
        String body = payload.get("body");
        if (subject == null || body == null) {
            return ResponseEntity.badRequest().body("Subject and body are required");
        }
        emailService.sendBroadcastEmail(subject, body);
        return ResponseEntity.ok("Broadcast started in background!");
    }

    /**
     * Deletes a community post by ID directly as an administrator.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/admin/delete-post/{id}}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, or {@code 404 Not Found}</li>
     * </ul>
     *
     * @param id the ID of the post to remove
     * @return a {@link ResponseEntity} confirming deletion
     */
    @DeleteMapping("/delete-post/{id}")
    @Transactional
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        if (!postRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        postRepository.deleteById(id);
        return ResponseEntity.ok("Post deleted by Admin.");
    }

    // =========================================================
    // 3. BULK UPLOAD (Optimized Logic)
    // =========================================================

    /**
     * Bulk-imports user accounts from an uploaded CSV ({@code .csv}) or Excel ({@code .xlsx}) file.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/admin/upload-users-excel}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@code multipart/form-data} with file parameter {@code "file"}</li>
     *   <li><b>Response:</b> {@code 200 OK} with count of imported users, or {@code 400/500} on error</li>
     * </ul>
     *
     * @param file the uploaded {@link MultipartFile} ({@code .csv} or {@code .xlsx})
     * @return a {@link ResponseEntity} summarizing the bulk upload result
     */
    @PostMapping("/upload-users-excel")
    @Transactional
    public ResponseEntity<?> uploadUsersUniversal(@RequestParam("file") MultipartFile file) {
        // Step 1: Validate that a non-empty file with a valid filename was provided
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Please select a file.");
        String fileName = file.getOriginalFilename();
        if (fileName == null) return ResponseEntity.badRequest().body("Invalid file.");

        // Step 2: Route to the appropriate parser based on file extension (.csv vs .xlsx)
        try {
            if (fileName.toLowerCase().endsWith(".csv")) {
                return processCSV(file);
            } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                return processExcel(file);
            } else {
                return ResponseEntity.badRequest().body("❌ Unsupported format. Please upload .csv or .xlsx");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error during upload: " + e.getMessage());
        }
    }

    /**
     * Parses an Excel ({@code .xlsx}) workbook using Apache POI and creates user records for each row.
     * Skips row 0 (header row) and any rows missing name or email.
     */
    private ResponseEntity<?> processExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1));
                if (name.isEmpty() || email.isEmpty()) continue;

                saveUserFromData(name, email,
                        getCellValue(row.getCell(2)), getCellValue(row.getCell(3)),
                        getCellValue(row.getCell(4)), getCellValue(row.getCell(5)),
                        getCellValue(row.getCell(6)), getCellValue(row.getCell(7)),
                        getCellValue(row.getCell(8)), getCellValue(row.getCell(9)),
                        getCellValue(row.getCell(10)), getCellValue(row.getCell(11))
                );
                count++;
            }
            return ResponseEntity.ok("✅ Successfully uploaded " + count + " users from Excel!");
        }
    }

    /**
     * Parses a CSV ({@code .csv}) file line by line (respecting quoted commas) and creates user records.
     */
    private ResponseEntity<?> processCSV(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int count = 0;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                // Regex handles commas inside quotes
                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (data.length < 2) continue;

                saveUserFromData(data[0].trim().replace("\"", ""), data[1].trim().replace("\"", ""),
                        (data.length > 2) ? data[2] : "STUDENT",
                        (data.length > 3) ? data[3] : "",
                        (data.length > 4) ? data[4] : "",
                        (data.length > 5) ? data[5] : "",
                        (data.length > 6) ? data[6] : "",
                        (data.length > 7) ? data[7] : "",
                        (data.length > 8) ? data[8] : "",
                        (data.length > 9) ? data[9] : "",
                        (data.length > 10) ? data[10] : "",
                        (data.length > 11) ? data[11] : ""
                );
                count++;
            }
            return ResponseEntity.ok("✅ Successfully uploaded " + count + " users from CSV!");
        }
    }

    /**
     * Helper method that constructs and persists a {@link User} from parsed spreadsheet columns.
     * Skips creation if a user with the given email already exists.
     */
    private void saveUserFromData(String name, String email, String roleStr, String batchStr, String enroll,
                                  String company, String desig, String head, String skills,
                                  String git, String linked, String exp) {

        if (userRepository.existsByEmail(email)) return;

        User user = new User();
        user.setName(name);
        user.setEmail(email);

        // Safe Enum Conversion
        try {
            user.setRole(Role.valueOf(roleStr.toUpperCase().trim()));
        } catch (Exception e) {
            user.setRole(Role.STUDENT);
        }

        // Clean Batch Year formatting (handles 2024.0 from Excel)
        try {
            if (!batchStr.isEmpty()) {
                String cleanBatch = batchStr.contains(".") ? batchStr.split("\\.")[0] : batchStr;
                user.setBatchYear(Integer.parseInt(cleanBatch));
            }
        } catch (Exception e) { /* Skip invalid years */ }

        user.setEnrollmentNumber(enroll);
        user.setCurrentCompany(company);
        user.setDesignation(desig);
        user.setHeadline(head);
        user.setSkills(skills);
        user.setGithubUrl(git);
        user.setLinkedinUrl(linked);
        user.setPastExperience(exp);
        user.setPasswordHash(passwordEncoder.encode("Bvicam@2025"));
        user.setPasswordChanged(false);

        userRepository.save(user);
    }

    /**
     * Safely extracts a trimmed string representation from an Apache POI {@link Cell}.
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long)cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
}