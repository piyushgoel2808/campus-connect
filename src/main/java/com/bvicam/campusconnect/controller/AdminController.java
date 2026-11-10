package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.*;
import com.bvicam.campusconnect.repository.*;
import com.bvicam.campusconnect.service.EmailService;
import com.bvicam.campusconnect.service.PostService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

/**
 * REST Controller for Administrative Operations ({@code /api/admin}).
 * Protected at both route level ({@code SecurityConfig}) and method/class level ({@code @PreAuthorize}).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final int MAX_BULK_IMPORT_ROWS = 2000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostService postService;
    private final DepartmentRepository departmentRepository;
    private final CommentRepository commentRepository;
    private final JobRepository jobRepository;
    private final FeedbackRepository feedbackRepository;
    private final EventRepository eventRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final NotificationRepository notificationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    @Autowired
    public AdminController(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           PostRepository postRepository,
                           PostLikeRepository postLikeRepository,
                           PostService postService,
                           DepartmentRepository departmentRepository,
                           CommentRepository commentRepository,
                           JobRepository jobRepository,
                           FeedbackRepository feedbackRepository,
                           EventRepository eventRepository,
                           PrivateMessageRepository privateMessageRepository,
                           NotificationRepository notificationRepository,
                           KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.postService = postService;
        this.departmentRepository = departmentRepository;
        this.commentRepository = commentRepository;
        this.jobRepository = jobRepository;
        this.feedbackRepository = feedbackRepository;
        this.eventRepository = eventRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.notificationRepository = notificationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    private Department resolveDepartment(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        String normalized = code.trim();
        if ("BA(JMC)".equalsIgnoreCase(normalized)) {
            normalized = "BJMC";
        }
        final String finalCode = normalized.toUpperCase(Locale.ROOT);
        return departmentRepository.findByCode(finalCode)
                .orElseGet(() -> departmentRepository.save(new Department(finalCode, finalCode)));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserProfileDto>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream()
                .map(UserProfileDto::fromEntity)
                .toList());
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Email is required!");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Name is required!");
        }
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("Error: Email already exists!");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
        user.setEnrollmentNumber(request.getEnrollmentNumber());
        user.setBatchYear(request.getBatchYear());

        if (request.getDepartmentCode() != null && !request.getDepartmentCode().trim().isEmpty()) {
            user.setDepartment(resolveDepartment(request.getDepartmentCode()));
        }

        user.setHeadline(request.getHeadline());
        user.setCurrentCompany(request.getCurrentCompany());
        user.setDesignation(request.getDesignation());
        user.setSkills(request.getSkills());
        user.setGithubUrl(request.getGithubUrl());
        user.setLinkedinUrl(request.getLinkedinUrl());
        user.setPastExperience(request.getPastExperience());

        String rawPass = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                ? request.getPassword().trim()
                : "Bvicam@2025";
        user.setPasswordHash(passwordEncoder.encode(rawPass));
        user.setPasswordChanged(false);

        userRepository.save(user);
        return ResponseEntity.ok("User created successfully!");
    }

    @DeleteMapping("/delete-user/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Principal principal) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        if (principal != null && principal.getName() != null
                && principal.getName().equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body("Administrators cannot delete their own account.");
        }

        // Clean up event RSVP join table rows using targeted query
        for (Event event : eventRepository.findByParticipantId(id)) {
            if (event.getParticipants() != null && event.getParticipants().remove(user)) {
                eventRepository.save(event);
            }
        }

        postLikeRepository.deleteByUserId(id);
        for (com.bvicam.campusconnect.entity.Comment comment : commentRepository.findByAuthorId(id)) {
            commentRepository.delete(comment);
        }
        for (Post post : postRepository.findByAuthorId(id)) {
            postLikeRepository.deleteByPostId(post.getId());
            postRepository.delete(post);
        }
        jobRepository.deleteByPostedById(id);
        feedbackRepository.deleteBySubmittedById(id);
        knowledgeDocumentRepository.clearUploaderByUserId(id);
        privateMessageRepository.deleteAllByParticipantEmail(user.getEmail());
        notificationRepository.deleteByRecipientEmail(user.getEmail());

        userRepository.delete(user);
        return ResponseEntity.ok("User deleted successfully.");
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody RegisterRequest updatedData) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        if (updatedData.getEmail() != null && !updatedData.getEmail().trim().isEmpty()) {
            String newEmail = updatedData.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                return ResponseEntity.badRequest().body("Error: Another user already uses this email.");
            }
            user.setEmail(newEmail);
        }
        if (updatedData.getName() != null && !updatedData.getName().trim().isEmpty()) {
            user.setName(updatedData.getName().trim());
        }
        if (updatedData.getRole() != null) user.setRole(updatedData.getRole());
        if (updatedData.getBatchYear() != null) user.setBatchYear(updatedData.getBatchYear());
        if (updatedData.getEnrollmentNumber() != null) user.setEnrollmentNumber(updatedData.getEnrollmentNumber());
        if (updatedData.getCurrentCompany() != null) user.setCurrentCompany(updatedData.getCurrentCompany());
        if (updatedData.getDesignation() != null) user.setDesignation(updatedData.getDesignation());
        if (updatedData.getHeadline() != null) user.setHeadline(updatedData.getHeadline());
        if (updatedData.getSkills() != null) user.setSkills(updatedData.getSkills());
        if (updatedData.getGithubUrl() != null) user.setGithubUrl(updatedData.getGithubUrl());
        if (updatedData.getLinkedinUrl() != null) user.setLinkedinUrl(updatedData.getLinkedinUrl());
        if (updatedData.getPastExperience() != null) user.setPastExperience(updatedData.getPastExperience());

        if (updatedData.getDepartmentCode() != null && !updatedData.getDepartmentCode().trim().isEmpty()) {
            user.setDepartment(resolveDepartment(updatedData.getDepartmentCode()));
        }

        userRepository.save(user);
        return ResponseEntity.ok("User updated successfully.");
    }

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

    @PutMapping("/users/{id}/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        user.setPasswordHash(passwordEncoder.encode("Bvicam@2025"));
        user.setPasswordChanged(false);
        userRepository.save(user);
        return ResponseEntity.ok("Password reset to 'Bvicam@2025'");
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> sendBroadcast(@RequestBody Map<String, String> payload) {
        String subject = payload != null ? payload.get("subject") : null;
        String body = payload != null ? payload.get("body") : null;
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body("Subject and body are required");
        }
        emailService.sendBroadcastEmail(subject.trim(), body.trim());
        return ResponseEntity.ok("Broadcast started in background!");
    }

    @DeleteMapping("/delete-post/{id}")
    @Transactional
    public ResponseEntity<?> deletePost(@PathVariable Long id, Principal principal) {
        String requester = principal != null ? principal.getName() : "admin";
        postService.deletePost(id, requester);
        return ResponseEntity.ok("Post deleted by Admin.");
    }

    @PostMapping("/upload-users-excel")
    @Transactional
    public ResponseEntity<?> uploadUsersUniversal(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            return ResponseEntity.badRequest().body("Invalid file.");
        }

        try {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                return processCSV(file);
            } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                return processExcel(file);
            } else {
                return ResponseEntity.badRequest().body("Unsupported format. Please upload .csv or .xlsx");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error during upload: " + e.getMessage());
        }
    }

    private ResponseEntity<?> processExcel(MultipartFile file) throws Exception {
        Set<String> existingEmails = new HashSet<>(userRepository.findAllEmails());
        String defaultHash = passwordEncoder.encode("Bvicam@2025");
        Map<String, Department> deptCache = new HashMap<>();
        List<User> toSave = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int maxRow = Math.min(sheet.getLastRowNum(), MAX_BULK_IMPORT_ROWS);
            for (int i = 1; i <= maxRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1)).toLowerCase(Locale.ROOT);
                if (name.isEmpty() || email.isEmpty() || existingEmails.contains(email)) continue;

                User user = buildBulkUser(
                        name, email,
                        getCellValue(row.getCell(2)), getCellValue(row.getCell(3)),
                        getCellValue(row.getCell(4)), getCellValue(row.getCell(5)),
                        getCellValue(row.getCell(6)), getCellValue(row.getCell(7)),
                        getCellValue(row.getCell(8)), getCellValue(row.getCell(9)),
                        getCellValue(row.getCell(10)), getCellValue(row.getCell(11)),
                        getCellValue(row.getCell(12)),
                        defaultHash, deptCache
                );
                existingEmails.add(email);
                toSave.add(user);
            }
        }
        userRepository.saveAll(toSave);
        return ResponseEntity.ok("Successfully uploaded " + toSave.size() + " users from Excel!");
    }

    private ResponseEntity<?> processCSV(MultipartFile file) throws Exception {
        Set<String> existingEmails = new HashSet<>(userRepository.findAllEmails());
        String defaultHash = passwordEncoder.encode("Bvicam@2025");
        Map<String, Department> deptCache = new HashMap<>();
        List<User> toSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return ResponseEntity.badRequest().body("CSV file is empty.");
            }
            String line;
            int rows = 0;
            while ((line = reader.readLine()) != null && rows < MAX_BULK_IMPORT_ROWS) {
                rows++;
                String cleanLine = line.replace("\uFEFF", "");
                String[] data = cleanLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (data.length < 2) continue;

                String name = stripCsvQuotes(data[0]);
                String email = stripCsvQuotes(data[1]).toLowerCase(Locale.ROOT);
                if (name.isEmpty() || email.isEmpty() || existingEmails.contains(email)) continue;

                User user = buildBulkUser(
                        name, email,
                        csvCol(data, 2, "STUDENT"),
                        csvCol(data, 3, ""),
                        csvCol(data, 4, ""),
                        csvCol(data, 5, ""),
                        csvCol(data, 6, ""),
                        csvCol(data, 7, ""),
                        csvCol(data, 8, ""),
                        csvCol(data, 9, ""),
                        csvCol(data, 10, ""),
                        csvCol(data, 11, ""),
                        csvCol(data, 12, ""),
                        defaultHash, deptCache
                );
                existingEmails.add(email);
                toSave.add(user);
            }
        }
        userRepository.saveAll(toSave);
        return ResponseEntity.ok("Successfully uploaded " + toSave.size() + " users from CSV!");
    }

    private String csvCol(String[] data, int idx, String fallback) {
        return data.length > idx ? stripCsvQuotes(data[idx]) : fallback;
    }

    private String stripCsvQuotes(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\"\"", "\"").trim();
    }

    private User buildBulkUser(String name, String email, String roleStr, String batchStr, String enroll,
                               String company, String desig, String head, String skills,
                               String git, String linked, String exp, String deptCode,
                               String defaultPasswordHash, Map<String, Department> deptCache) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);

        try {
            user.setRole(Role.valueOf(roleStr.toUpperCase(Locale.ROOT).trim()));
        } catch (Exception e) {
            user.setRole(Role.STUDENT);
        }

        try {
            if (!batchStr.isEmpty()) {
                String cleanBatch = batchStr.contains(".") ? batchStr.split("\\.")[0] : batchStr;
                user.setBatchYear(Integer.parseInt(cleanBatch.trim()));
            }
        } catch (Exception ignored) {
        }

        if (deptCode != null && !deptCode.isEmpty()) {
            String key = deptCode.trim().toUpperCase(Locale.ROOT);
            Department dept = deptCache.computeIfAbsent(key, this::resolveDepartment);
            user.setDepartment(dept);
        }

        user.setEnrollmentNumber(enroll);
        user.setCurrentCompany(company);
        user.setDesignation(desig);
        user.setHeadline(head);
        user.setSkills(skills);
        user.setGithubUrl(git);
        user.setLinkedinUrl(linked);
        user.setPastExperience(exp);
        user.setPasswordHash(defaultPasswordHash);
        user.setPasswordChanged(false);
        return user;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
