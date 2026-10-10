package com.bvicam.campusconnect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test suite for the Campus Connect (GradLink) Spring Boot application.
 *
 * <p>Uses the {@code "test"} profile ({@code src/test/resources/application-test.properties})
 * to verify that the Spring application context, JPA repositories, security filter chain,
 * and WebSocket configuration beans initialize without errors.
 */
@ActiveProfiles("test")
@SpringBootTest
class CampusConnectApplicationTests {

	/**
	 * Verifies that the complete Spring Boot application context loads cleanly.
	 */
	@Test
	void contextLoads() {
		// Context initialization is validated automatically by @SpringBootTest
	}

}
