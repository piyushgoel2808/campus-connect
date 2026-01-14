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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException; // Good practice to have this too

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
    // 8. Universal Bulk Upload (Supports both .xlsx and .csv)
    @PostMapping("/upload-users-excel")
    public ResponseEntity<?> uploadUsersUniversal(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Please select a file.");

        String fileName = file.getOriginalFilename();
        if (fileName == null) return ResponseEntity.badRequest().body("Invalid file.");

        try {
            // 🔀 BRANCH LOGIC: Check file extension
            if (fileName.toLowerCase().endsWith(".csv")) {
                return processCSV(file);
            } else if (fileName.toLowerCase().endsWith(".xlsx")) {
                return processExcel(file);
            } else {
                return ResponseEntity.badRequest().body("❌ Unsupported format. Please upload .csv or .xlsx");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // --- HELPER: Process Excel (.xlsx) ---
    private ResponseEntity<?> processExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int count = 0;
            int rowNum = 0;

            for (Row row : sheet) {
                if (rowNum++ == 0) continue; // Skip Header

                // Extract Data using the helper
                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1));
                if (name.isEmpty() || email.isEmpty()) continue;

                // Call the save user logic
                saveUserFromData(name, email,
                        getCellValue(row.getCell(2)), // Role
                        getCellValue(row.getCell(3)), // Batch
                        getCellValue(row.getCell(4)), // Enrollment
                        getCellValue(row.getCell(5)), // Company
                        getCellValue(row.getCell(6)), // Designation
                        getCellValue(row.getCell(7)), // Headline
                        getCellValue(row.getCell(8)), // Skills
                        getCellValue(row.getCell(9)), // GitHub
                        getCellValue(row.getCell(10)),// LinkedIn
                        getCellValue(row.getCell(11)) // Experience
                );
                count++;
            }
            return ResponseEntity.ok("✅ Successfully uploaded " + count + " users from Excel!");
        }
    }

    // --- HELPER: Process CSV (.csv) ---
    private ResponseEntity<?> processCSV(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int count = 0;
            int rowNum = 0;

            while ((line = reader.readLine()) != null) {
                if (rowNum++ == 0) continue; // Skip Header

                // Regex to split by comma ONLY if not inside quotes (Handles "C++, DSA")
                String[] data = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                if (data.length < 2) continue; // Skip empty rows

                // Clean up quotes from data if present (e.g. "C++, DSA" -> C++, DSA)
                for(int i=0; i<data.length; i++) {
                    data[i] = data[i].trim().replace("\"", "");
                }

                String name = data[0];
                String email = data[1];
                if (name.isEmpty() || email.isEmpty()) continue;

                // Safely get other fields (check length to prevent index errors)
                saveUserFromData(name, email,
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

    // --- HELPER: Common Save Logic ---
    private void saveUserFromData(String name, String email, String role, String batchStr, String enroll,
                                  String company, String desig, String head, String skills,
                                  String git, String linked, String exp) {

        if (userRepository.existsByEmail(email)) return; // Skip duplicates

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRole(role.toUpperCase());

        try {
            if (!batchStr.isEmpty()) user.setBatchYear((int) Double.parseDouble(batchStr));
        } catch (Exception e) {}

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