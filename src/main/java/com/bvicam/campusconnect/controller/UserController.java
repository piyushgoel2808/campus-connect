package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // 1. Get All Users (For Directory)
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // 2. Get MY Profile (To fill the edit form)
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        return ResponseEntity.ok(user);
    }

    // 3. Update Profile (The LinkedIn Feature)
// Import the new DTO at the top


    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserProfileDto profileDto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        // Debugging
        System.out.println("Received Profile Update for: " + email);

        // Update fields from DTO
        if (profileDto.getHeadline() != null) user.setHeadline(profileDto.getHeadline());
        if (profileDto.getCurrentCompany() != null) user.setCurrentCompany(profileDto.getCurrentCompany());
        if (profileDto.getDesignation() != null) user.setDesignation(profileDto.getDesignation());
        if (profileDto.getSkills() != null) user.setSkills(profileDto.getSkills());
        if (profileDto.getPastExperience() != null) user.setPastExperience(profileDto.getPastExperience());
        if (profileDto.getLinkedinUrl() != null) user.setLinkedinUrl(profileDto.getLinkedinUrl());
        if (profileDto.getGithubUrl() != null) user.setGithubUrl(profileDto.getGithubUrl());

        userRepository.save(user);
        return ResponseEntity.ok("Profile Updated Successfully!");
    }
}