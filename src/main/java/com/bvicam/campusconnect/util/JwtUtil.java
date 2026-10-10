package com.bvicam.campusconnect.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility component responsible for JSON Web Token (JWT) creation and cryptographic key management.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Used by {@code AuthController} upon successful login to issue a signed bearer token
 *       identifying the authenticated user by their email address.</li>
 *   <li>Used by {@link com.bvicam.campusconnect.config.JwtAuthenticationFilter} on every subsequent
 *       HTTP request to obtain the HMAC-SHA256 signing key ({@link #getSignKey()}) and verify token
 *       integrity and expiration.</li>
 * </ul>
 */
@Component
public class JwtUtil {

    // =========================================================================
    // Configuration Properties
    // =========================================================================

    /**
     * Base64-encoded 256-bit secret key used to sign and verify JWT tokens.
     * Injected from {@code app.jwt.secret} in {@code application.properties} (with a default
     * fallback value for local development).
     */
    @Value("${app.jwt.secret:5367566B59703373367639792B423F4528482B4D6251655468576D5A71347437}")
    private String secret;

    // =========================================================================
    // Token Generation & Key Resolution
    // =========================================================================

    /**
     * Generates a signed JWT for the specified user identifier (email address).
     *
     * @param userName the authenticated user's email address, stored as the JWT {@code sub} (subject) claim
     * @return a compact, URL-safe JWT string valid for 10 hours
     */
    public String generateToken(String userName) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userName);
    }

    /**
     * Builds and signs the JWT payload with standard registered claims ({@code sub}, {@code iat}, {@code exp})
     * using the HMAC-SHA256 ({@link SignatureAlgorithm#HS256}) algorithm.
     *
     * @param claims  custom claims map to embed in the token payload
     * @param subject the principal identifier (user email)
     * @return the serialized JWT string
     */
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 Hours validity
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Decodes the Base64-encoded secret string into raw bytes and constructs an HMAC-SHA cryptographic
     * {@link Key} suitable for signing and verifying HS256 JWTs.
     *
     * @return the cryptographic signing {@link Key}
     */
    public Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}