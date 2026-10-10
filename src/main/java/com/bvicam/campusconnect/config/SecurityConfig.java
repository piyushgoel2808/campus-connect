package com.bvicam.campusconnect.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the Campus Connect application.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Configures a <b>stateless</b> JWT-based security architecture ({@link SessionCreationPolicy#STATELESS})
 *       where no server-side HTTP session cookies are used for API authentication.</li>
 *   <li>Whitelists static frontend pages, JS/CSS bundles, uploaded media ({@code /uploads/**}),
 *       WebSocket handshake endpoints ({@code /ws/**}), and authentication endpoints ({@code /api/auth/**}).</li>
 *   <li>Protects all other REST API endpoints ({@code /api/**}), requiring a valid Bearer token
 *       processed by {@link JwtAuthenticationFilter}.</li>
 *   <li>Provides global CORS, {@link PasswordEncoder} (BCrypt), and {@link AuthenticationManager} beans.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // =========================================================================
    // Dependencies
    // =========================================================================

    /** Custom filter that validates Bearer JWTs on incoming requests before standard auth filters run. */
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // =========================================================================
    // Security Filter Chain & Access Control Rules
    // =========================================================================

    /**
     * Defines the HTTP security filter chain, route authorization matrix, session policy, and JWT filter placement.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain} bean
     * @throws Exception if an error occurs while building the security configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF protection since the API is stateless and authenticates via Bearer JWT headers, not cookies
                .csrf(csrf -> csrf.disable())
                // Enable Cross-Origin Resource Sharing using the custom configuration source below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Allow same-origin iframes (useful for embedded previews or H2 console during development)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                // Enforce stateless session management: Spring Security will never create or use an HttpSession
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ✅ ADDED "/favicon.ico" here to fix the 403 error
                        // Public static frontend pages, scripts, stylesheets, SockJS/WebSocket handshake, and uploaded files
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/dashboard.html",
                                "/app.js",
                                "/dashboard.js",
                                "/components/**",
                                "/css/**",
                                "/js/**",
                                "/ws/**",
                                "/favicon.ico",
                                "/uploads/**"
                        ).permitAll()
                        // Public authentication endpoints (login, registration, departments list)
                        .requestMatchers("/api/auth/**").permitAll()
                        // Allow Spring Boot's default error dispatch route so error responses aren't masked as 403
                        .requestMatchers("/error").permitAll()
                        // Every other API endpoint requires a valid authenticated security context
                        .anyRequest().authenticated()
                )
                // Insert our JWT filter ahead of UsernamePasswordAuthenticationFilter in the filter pipeline
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // =========================================================================
    // CORS, Password Hashing & Authentication Manager Beans
    // =========================================================================

    /**
     * Configures global Cross-Origin Resource Sharing (CORS) rules so the frontend can communicate
     * with the backend across origins (e.g., local dev servers or cloud deployment domains).
     *
     * @return the {@link UrlBasedCorsConfigurationSource} mapped to all paths ({@code /**})
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow specific patterns + Credentials
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowCredentials(true);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Provides the BCrypt password encoder used across the application to hash passwords during
     * registration/seeding and verify passwords during login.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes Spring Security's {@link AuthenticationManager} as a bean so controllers/services
     * can programmatically authenticate credentials if needed.
     *
     * @param config the global {@link AuthenticationConfiguration}
     * @return the configured {@link AuthenticationManager}
     * @throws Exception if the AuthenticationManager cannot be retrieved
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}