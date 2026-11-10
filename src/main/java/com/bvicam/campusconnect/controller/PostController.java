package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.CreatePostRequest;
import com.bvicam.campusconnect.entity.Comment;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for the Community Feed, Post Attachments, and Nested Comments ({@code /api/posts}).
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final Map<String, String> ALLOWED_MIME_TO_EXT = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    @Autowired
    private PostService postService;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @GetMapping
    public List<Post> getAllPosts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            return postService.getPostsPage(page != null ? page : 0, size != null ? size : 20);
        }
        return postService.getAllPosts();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }

        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        String extension = ALLOWED_MIME_TO_EXT.get(contentType);
        if (extension == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PNG, JPEG, GIF, or WebP image files are allowed."));
        }

        try {
            byte[] bytes = file.getBytes();
            if (!isValidImagePayload(bytes, extension)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is not a valid raster image."));
            }

            Path uploadPath = Paths.get(uploadDir, "posts").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String filename = "post_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            Path targetLocation = uploadPath.resolve(filename).normalize();
            if (!targetLocation.startsWith(uploadPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid target path."));
            }
            Files.write(targetLocation, bytes);

            String imageUrl = "/uploads/posts/" + filename;
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload image."));
        }
    }

    private boolean isValidImagePayload(byte[] bytes, String extension) throws IOException {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return switch (extension) {
            case ".png" -> (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47)
                    && ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            case ".jpg" -> (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF)
                    && ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            case ".gif" -> (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8')
                    && ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            case ".webp" -> bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody CreatePostRequest request, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            Post created = postService.createPost(request, email);
            return ResponseEntity.ok(Map.of("message", "Post created successfully", "id", created.getId()));
        } catch (IllegalArgumentException e) {
            boolean flagged = e.getMessage() != null && e.getMessage().contains("flagged");
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "flagged", flagged));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        postService.deletePost(id, email);
        return ResponseEntity.ok(Map.of("message", "Post deleted"));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> likePost(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(postService.toggleLike(id, email));
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<?> togglePin(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        boolean pinned = postService.togglePin(id, email);
        return ResponseEntity.ok(Map.of("message", "Pin status updated", "isPinned", pinned));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        String content = body != null && body.get("content") != null ? body.get("content").toString() : null;
        Long parentId = null;
        if (body != null && body.get("parentId") != null) {
            try {
                parentId = Long.valueOf(body.get("parentId").toString());
            } catch (NumberFormatException ignored) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid parentId"));
            }
        }
        try {
            Comment comment = postService.addComment(id, content, parentId, email);
            return ResponseEntity.ok(comment);
        } catch (IllegalArgumentException e) {
            boolean flagged = e.getMessage() != null && e.getMessage().contains("flagged");
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "flagged", flagged));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, Principal principal) {
        String email = principal != null ? principal.getName() : SecurityContextHolder.getContext().getAuthentication().getName();
        int remainingComments = postService.deleteComment(commentId, email);
        return ResponseEntity.ok(Map.of("message", "Comment deleted", "commentsCount", remainingComments));
    }
}
