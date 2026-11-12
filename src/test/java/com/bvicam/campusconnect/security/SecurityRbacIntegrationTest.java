package com.bvicam.campusconnect.security;

import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Spring Security filter-chain and method-security RBAC integration tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityRbacIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;
    private String studentToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ensureUser("student@bvicam.in", "Test Student", Role.STUDENT, "1234");
        ensureUser("admin@bvicam.in", "Test Admin", Role.ADMIN, "admin123");

        this.studentToken = jwtUtil.generateToken("student@bvicam.in");
        this.adminToken = jwtUtil.generateToken("admin@bvicam.in");
    }

    private void ensureUser(String email, String name, Role role, String rawPassword) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setRole(role);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setPasswordChanged(true);
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("Protected REST endpoints reject unauthenticated callers with HTTP 401")
    void unauthenticatedRequests_returnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/knowledge/documents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"1234\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register rejects ADMIN role escalation (403) and allows valid STUDENT signup (200)")
    void selfRegistration_blocksAdminAndAllowsStudent() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rogue Admin\",\"email\":\"rogue-admin@bvicam.in\",\"password\":\"secret123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        String uniqueEmail = "student-" + System.nanoTime() + "@bvicam.in";
        try {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"New Student\",\"email\":\"" + uniqueEmail + "\",\"password\":\"secret123\",\"role\":\"STUDENT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());

            User saved = userRepository.findByEmail(uniqueEmail).orElseThrow();
            assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
            assertThat(saved.getPasswordChanged()).isTrue();
        } finally {
            userRepository.findByEmail(uniqueEmail).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("STUDENT tokens are blocked (403) from ADMIN-only and Alumni/Admin routes")
    void studentRole_forbiddenFromPrivilegedEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/delete-user/999999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/knowledge/documents")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/feedback")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Unauthorized Event\",\"dateTime\":\"2030-01-10T10:00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backend Engineer\",\"company\":\"Acme\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN tokens can access admin routes while SSRF scrape URLs are blocked with HTTP 400")
    void adminRole_authorizedAndSsrfUrlsRejected() throws Exception {
        mockMvc.perform(get("/api/feedback")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/knowledge/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/knowledge/scrape-url")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:8080/actuator\",\"title\":\"Localhost Probe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/admin/knowledge/scrape-url")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://169.254.169.254/latest/meta-data/\",\"title\":\"Metadata Probe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /api/auth/change-password validates current password for authenticated callers")
    void changePassword_verifiesCurrentPassword() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"brandnewpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect."));
    }

    @Test
    @DisplayName("POST /api/bot/runner-endpoint bypasses JWT filter cleanly and fails closed when auth token is unset")
    void runnerEndpointSync_failsClosedWhenTokenUnset() throws Exception {
        mockMvc.perform(post("/api/bot/runner-endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://test-tunnel.trycloudflare.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }
}
