package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 * <p>
 * Bridges the application's {@link User} JPA entity with Spring Security's authentication
 * pipeline during login ({@code AuthenticationManager.authenticate(...)}) and JWT filter
 * verification ({@code JwtFilter}).
 * </p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Loads a user's security credentials from the database using their email address.
     * <p>
     * In Campus Connect, the user's email acts as the unique username identifier.
     * </p>
     *
     * @param email the email address submitted during login or extracted from a JWT token
     * @return a Spring Security {@link UserDetails} instance containing the email, hashed password, and authorities
     * @throws UsernameNotFoundException if no user account exists with the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Step 1: Look up the user by email in the database (throws if not found)
        // Find user by Email (since we use email to login)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Step 2: Wrap the database entity into a Spring Security UserDetails object
        // Return a Spring Security User object (Email, Password, Roles)
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                new ArrayList<>() // We will add Roles/Authorities later
        );
    }
}