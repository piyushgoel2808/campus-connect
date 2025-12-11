package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // This endpoint is AUTOMATICALLY protected by our SecurityConfig
    // You cannot access it without a valid JWT Token
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}