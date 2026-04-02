package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.entity.Role;
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

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PostRepository postRepository;

    @Autowired
    public AdminController(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           PostRepository postRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.postRepository = postRepository;
    }

    // =========================================================
    // 1. USER MANAGEMENT (CRUD)
    // =========================================================

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body("Error: Email already exists!");
            }

            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            // Safe Enum assignment from Version 2
            user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
            user.setEnrollmentNumber(request.getEnrollmentNumber());
            user.setBatchYear(request.getBatchYear());

            String defaultPass = "Bvicam@2025";
            user.setPasswordHash(passwordEncoder.encode(defaultPass));
            user.setPasswordChanged(false);

            userRepository.save(user);
            return ResponseEntity.ok("✅ User created successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating user: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete-user/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("✅ User deleted successfully.");
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedData) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(updatedData.getName());
        user.setEmail(updatedData.getEmail());
        user.setRole(updatedData.getRole());
        user.setBatchYear(updatedData.getBatchYear());
        user.setEnrollmentNumber(updatedData.getEnrollmentNumber());
        user.setCurrentCompany(updatedData.getCurrentCompany());
        user.setDesignation(updatedData.getDesignation());
        user.setHeadline(updatedData.getHeadline());
        user.setSkills(updatedData.getSkills());
        user.setGithubUrl(updatedData.getGithubUrl());
        user.setLinkedinUrl(updatedData.getLinkedinUrl());
        user.setPastExperience(updatedData.getPastExperience());

        userRepository.save(user);
        return ResponseEntity.ok("✅ User updated successfully.");
    }

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

    @PostMapping("/upload-users-excel")
    @Transactional
    public ResponseEntity<?> uploadUsersUniversal(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Please select a file.");
        String fileName = file.getOriginalFilename();
        if (fileName == null) return ResponseEntity.badRequest().body("Invalid file.");

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