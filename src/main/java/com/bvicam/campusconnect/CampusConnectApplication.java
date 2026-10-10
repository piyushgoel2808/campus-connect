package com.bvicam.campusconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the <b>Campus Connect</b> Spring Boot application.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>{@link SpringBootApplication}: Enables Spring component scanning across the
 *       {@code com.bvicam.campusconnect} package hierarchy, Spring Boot auto-configuration,
 *       and extra bean registration.</li>
 *   <li>{@link EnableAsync}: Activates Spring's asynchronous method execution capability
 *       (used by background tasks such as {@code EmailService} and {@code NotificationService}
 *       annotated with {@code @Async} so slow I/O like SMTP email sending never blocks
 *       HTTP request threads).</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
public class CampusConnectApplication {

	/**
	 * Bootstraps the embedded Tomcat server, initializes the Spring application context,
	 * runs database migrations/seeding ({@link com.bvicam.campusconnect.config.DataSeeder}),
	 * and begins listening for HTTP and WebSocket connections.
	 *
	 * @param args command-line arguments passed at startup
	 */
	public static void main(String[] args) {
		SpringApplication.run(CampusConnectApplication.class, args);
	}

}

