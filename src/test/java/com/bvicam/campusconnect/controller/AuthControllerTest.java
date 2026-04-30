package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.AuthResponse;
import com.bvicam.campusconnect.dto.LoginRequest;
import com.bvicam.campusconnect.dto.RegisterRequest;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.DepartmentRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginReturnsUserIdAndToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@example.com");
        request.setPassword("secret");

        Authentication authentication = new UsernamePasswordAuthenticationToken("student@example.com", "secret");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtil.generateToken("student@example.com")).thenReturn("jwt-token");

        User user = new User();
        user.setId(42L);
        user.setName("Student");
        user.setEmail("student@example.com");
        user.setRole(Role.STUDENT);
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = authController.login(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);

        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body.getToken()).isEqualTo("jwt-token");
        assertThat(body.getId()).isEqualTo(42L);
        assertThat(body.getName()).isEqualTo("Student");
        assertThat(body.getRole()).isEqualTo("STUDENT");
    }

    @Test
    void registerEncodesPasswordAndSavesUser() {
        RegisterRequest request = new RegisterRequest();
        request.setName("New Student");
        request.setEmail("new.student@example.com");
        request.setPassword("plain-password");
        request.setRole(Role.STUDENT);
        request.setEnrollmentNumber("ENR-001");

        when(userRepository.existsByEmail("new.student@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = authController.register(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.STUDENT);
    }
}