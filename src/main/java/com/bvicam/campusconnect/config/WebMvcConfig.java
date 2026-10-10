package com.bvicam.campusconnect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring MVC configuration that exposes user-uploaded files (such as wall post images)
 * as static HTTP resources.
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>When a user creates a post with an image via {@code PostController}, the file is saved
 *       on disk inside the directory configured by {@code upload.dir} (defaults to {@code ./uploads}
 *       locally or {@code /app/uploads} in Docker).</li>
 *   <li>This class maps incoming HTTP {@code GET /uploads/**} requests directly to that filesystem
 *       directory so images render seamlessly in the browser.</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Filesystem path where uploaded media files are stored.
     * Injected from {@code upload.dir} property (or {@code UPLOAD_DIR} environment variable),
     * defaulting to {@code "uploads"} relative to the working directory.
     */
    @Value("${upload.dir:uploads}")
    private String uploadDir;

    /**
     * Registers a static resource handler mapping {@code /uploads/**} URLs to the resolved
     * {@code file:///...} URI of the local upload directory.
     *
     * @param registry Spring MVC's resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the configured directory to a normalized absolute path and convert to a file:// URI
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadUri = uploadPath.toUri().toString();

        // Spring ResourceLocations require a trailing slash to treat the URI as a directory
        if (!uploadUri.endsWith("/")) {
            uploadUri += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadUri);
    }
}

