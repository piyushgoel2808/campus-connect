package com.bvicam.campusconnect.config;

import com.bvicam.campusconnect.service.CustomUserDetailsService;
import com.bvicam.campusconnect.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless HTTP security filter that intercepts every incoming request once to authenticate
 * users via JSON Web Tokens (JWT).
 *
 * <p><b>Architectural Role & Filter Chain Execution:</b></p>
 * <ol>
 *   <li>Registered before {@code UsernamePasswordAuthenticationFilter} in {@link SecurityConfig}.</li>
 *   <li>Inspects the {@code Authorization} HTTP header for a {@code "Bearer <token>"} value.</li>
 *   <li>Parses and verifies the JWT cryptographic signature and expiration using {@link JwtUtil#getSignKey()}.</li>
 *   <li>Loads the user's authorities via {@link CustomUserDetailsService} and populates Spring Security's
 *       {@link SecurityContextHolder} so downstream controllers can access the authenticated {@code Principal}.</li>
 *   <li>If no token is present (e.g., public static assets or {@code /api/auth/**}), the filter simply delegates
 *       to the next filter in the chain without setting authentication.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // =========================================================================
    // Collaborating Dependencies
    // =========================================================================

    /** Utility providing the HMAC signing key used to verify incoming JWT signatures. */
    @Autowired
    private JwtUtil jwtUtil;

    /** Service that loads user credentials and role authorities from the {@code users} database table. */
    @Autowired
    private CustomUserDetailsService userDetailsService;

    // =========================================================================
    // Filter Execution Logic
    // =========================================================================

    /**
     * Extracts and validates the JWT bearer token from the request header and establishes the
     * Spring Security authentication context for the current request thread.
     *
     * @param request     incoming HTTP servlet request
     * @param response    outgoing HTTP servlet response
     * @param filterChain Spring Security filter chain to continue request processing
     * @throws ServletException if a servlet error occurs down the chain
     * @throws IOException      if an I/O error occurs down the chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Get the Authorization Header from the incoming HTTP request
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        // 2. Check if the header is present and follows the standard "Bearer <JWT>" convention
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove the 7-character "Bearer " prefix
            try {
                // 3. Parse claims, verify signature & expiration, and extract Username (email) from Token subject
                username = io.jsonwebtoken.Jwts.parserBuilder()
                        .setSigningKey(jwtUtil.getSignKey()) // Uses the secret key from JwtUtil
                        .build()
                        .parseClaimsJws(token)
                        .getBody()
                        .getSubject();
            } catch (Exception e) {
                // Token is malformed, tampered with, or expired; leave username null so request remains unauthenticated
                System.out.println("Invalid Token: " + e.getMessage());
            }
        }

        // 4. Validate Token and set Authentication context if user is not already authenticated in this thread
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // In a real app, validate expiration here too. For now, we trust the parsing (parseClaimsJws checks exp).
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            // Attach request-specific metadata (remote IP address, session ID if any) to the authentication token
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // This is the "Stamp of Approval" - User is now logged in for the duration of this HTTP request
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // 5. Continue processing the request through the remaining security filters and target controller
        filterChain.doFilter(request, response);
    }
}