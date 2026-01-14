package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PostRepository postRepository; // ✅ Fixed: Repository is now available

    // ✅ Constructor Injection (Removes "Field injection not recommended" warning)
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

    // 1. Create User (Onboarding)
    @PostMapping("/create-user")
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists!");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setEnrollmentNumber(request.getEnrollmentNumber());

        // Default password logic
        String defaultPass = "Bvicam@2025";
        user.setPasswordHash(passwordEncoder.encode(defaultPass));
        user.setPasswordChanged(false);

        // ✅ Fixed: Saves Batch Year (Requires RegisterRequest to have getBatchYear())
        if (request.getBatchYear() != null) {
            user.setBatchYear(request.getBatchYear());
        }

        userRepository.save(user);
        return ResponseEntity.ok("User created with default password: " + defaultPass);
    }

    // 2. Delete User
    @DeleteMapping("/delete-user/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully.");
    }

    // 3. Broadcast Email
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

    // 4. Moderate Content (Delete Any Post)
    @DeleteMapping("/delete-post/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        // ✅ Fixed: Now uses the injected postRepository
        if (!postRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        postRepository.deleteById(id);
        return ResponseEntity.ok("Post deleted by Admin.");
    }

    // 5. Update User (Robust Version)
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedData) {
        System.out.println("📝 Admin attempting to update user ID: " + id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update fields safely
        if (updatedData.getName() != null) user.setName(updatedData.getName());
        if (updatedData.getEmail() != null) user.setEmail(updatedData.getEmail());
        if (updatedData.getRole() != null) user.setRole(updatedData.getRole());

        // Update Batch Year
        if (updatedData.getBatchYear() != null) user.setBatchYear(updatedData.getBatchYear());

        // Update Professional details
        if (updatedData.getHeadline() != null) user.setHeadline(updatedData.getHeadline());
        if (updatedData.getSkills() != null) user.setSkills(updatedData.getSkills());
        if (updatedData.getCurrentCompany() != null) user.setCurrentCompany(updatedData.getCurrentCompany());

        try {
            userRepository.save(user);
            System.out.println("✅ User " + id + " updated successfully!");
            return ResponseEntity.ok("User updated successfully.");
        } catch (Exception e) {
            System.err.println("❌ Error saving user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    // 8. Bulk Upload Users via EXCEL (.xlsx)
    @PostMapping("/upload-users-excel")
    public ResponseEntity<?> uploadUsersExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Please select an Excel file.");

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int count = 0;
            int rowNum = 0;

            for (Row row : sheet) {
                // Skip Header Row
                if (rowNum++ == 0) continue;

                // 1. Mandatory Fields
                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1));
                String role = getCellValue(row.getCell(2)).toUpperCase();

                // Validation: Skip empty rows or duplicates
                if (name.isEmpty() || email.isEmpty() || userRepository.existsByEmail(email)) continue;

                User user = new User();
                user.setName(name);
                user.setEmail(email);
                user.setRole(role);

                // 2. Numeric Fields (Batch Year)
                try {
                    String batchStr = getCellValue(row.getCell(3));
                    if (!batchStr.isEmpty()) {
                        user.setBatchYear((int) Double.parseDouble(batchStr));
                    }
                } catch (Exception e) { /* Ignore number errors */ }

                // 3. Optional String Fields (Cols 4 - 11)
                user.setEnrollmentNumber(getCellValue(row.getCell(4)));
                user.setCurrentCompany(getCellValue(row.getCell(5)));
                user.setDesignation(getCellValue(row.getCell(6)));
                user.setHeadline(getCellValue(row.getCell(7)));
                user.setSkills(getCellValue(row.getCell(8)));
                user.setGithubUrl(getCellValue(row.getCell(9)));
                user.setLinkedinUrl(getCellValue(row.getCell(10)));
                user.setPastExperience(getCellValue(row.getCell(11)));

                // 4. System Defaults (Security)
                user.setPasswordHash(passwordEncoder.encode("Bvicam@2025")); // Default Password
                user.setPasswordChanged(false);

                userRepository.save(user);
                count++;
            }
            return ResponseEntity.ok("✅ Uploaded " + count + " users successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    // Helper to extract string from any cell type (String or Numeric)
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            default: return "";
        }
    }
}