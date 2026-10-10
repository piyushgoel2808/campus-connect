package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.entity.Comment;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for the Community Feed, Post Attachments, and Nested Comments ({@code /api/posts}).
 * <p>
 * Handles retrieving the campus feed, uploading post images to local storage, creating and deleting
 * posts (with automated profanity screening and user flagging), liking posts, pinning announcements
 * (Admin only), and managing threaded post comments and replies.
 * </p>
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    /** Base directory for storing uploaded media files (defaults to {@code uploads}). */
    @Value("${upload.dir:uploads}")
    private String uploadDir;

    // =========================================================
    // 1. FEED & IMAGE UPLOAD ENDPOINTS
    // =========================================================

    /**
     * Retrieves all community feed posts (pinned posts first, then newest first).
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/posts}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> JSON array of {@link Post} entities</li>
     * </ul>
     *
     * @return a sorted list of all {@link Post} objects
     */
    @GetMapping
    public List<Post> getAllPosts() {
        return postService.getAllPosts();
    }

    /**
     * Uploads an image attachment for a community post and stores it under {@code uploads/posts/}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/posts/upload}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@code multipart/form-data} with parameter {@code "file"} (must be {@code image/*})</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@code {"imageUrl": "/uploads/posts/..."}} or {@code 400/500} error</li>
     * </ul>
     *
     * @param file the uploaded image {@link MultipartFile}
     * @return a {@link ResponseEntity} containing the relative URL of the saved image
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        // Step 1: Validate that a non-empty image file was selected
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files (JPEG, PNG, GIF, WebP) are allowed."));
        }

        try {
            // Step 2: Ensure target directory (uploads/posts) exists on disk
            Path uploadPath = Paths.get(uploadDir, "posts").toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Step 3: Generate a collision-free filename preserving the original file extension
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String filename = "post_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            Path targetLocation = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Step 4: Return the web-accessible path served by WebConfig
            String imageUrl = "/uploads/posts/" + filename;
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload image: " + e.getMessage()));
        }
    }

    // =========================================================
    // 2. POST CRUD, LIKES & PINNING
    // =========================================================

    /**
     * Creates a new community post. Rejects the post with {@code 400 Bad Request} and flags
     * the user's account if profanity is detected.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/posts}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Request Payload:</b> {@link Post} JSON object ({@code content}, optional {@code imageUrl})</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 400 Bad Request} with {@code {"error": ..., "flagged": true}}</li>
     * </ul>
     *
     * @param post the {@link Post} payload to create
     * @return a {@link ResponseEntity} indicating creation success or moderation rejection
     */
    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody Post post) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            postService.createPost(post, email);
            return ResponseEntity.ok(Map.of("message", "Post created successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "flagged", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deletes a post by ID if the requester is the post's author or an {@code ADMIN}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/posts/{id}}</li>
     *   <li><b>Access Role:</b> Post Author or {@code ADMIN}</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, or {@code 403 Forbidden} if unauthorized</li>
     * </ul>
     *
     * @param id the ID of the post to delete
     * @return a {@link ResponseEntity} confirming deletion or error
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            postService.deletePost(id, email);
            return ResponseEntity.ok(Map.of("message", "Post deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Increments the like counter for a specific post.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/posts/{id}/like}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post to like</li>
     *   <li><b>Response:</b> {@code 200 OK} with {@code {"message": "Liked"}}</li>
     * </ul>
     *
     * @param id the ID of the post to like
     * @return a {@link ResponseEntity} confirming the like
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likePost(@PathVariable Long id) {
        postService.likePost(id);
        return ResponseEntity.ok(Map.of("message", "Liked"));
    }

    /**
     * Toggles the pinned status of a post at the top of the community feed.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/posts/{id}/pin}</li>
     *   <li><b>Access Role:</b> Authenticated Admin ({@code ADMIN}) only</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post to pin/unpin</li>
     *   <li><b>Response:</b> {@code 200 OK} on success, or {@code 403 Forbidden} if non-admin</li>
     * </ul>
     *
     * @param id the ID of the post to pin or unpin
     * @return a {@link ResponseEntity} confirming the pin status change
     */
    @PostMapping("/{id}/pin")
    public ResponseEntity<?> togglePin(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            postService.togglePin(id, email);
            return ResponseEntity.ok(Map.of("message", "Pin status updated"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================
    // 3. COMMENTS & NESTED REPLIES ENDPOINTS
    // =========================================================

    // --- COMMENTS ENDPOINTS ---

    /**
     * Retrieves all top-level comments (along with their nested {@code replies}) for a post.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/posts/{id}/comments}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post</li>
     *   <li><b>Response:</b> {@code 200 OK} with JSON array of top-level {@link Comment} objects</li>
     * </ul>
     *
     * @param id the ID of the post
     * @return a {@link ResponseEntity} containing the list of comments
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getComments(id));
    }

    /**
     * Adds a new top-level comment or nested reply to a post. Screens for profanity and flags
     * the author's account if abusive language is detected.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code POST /api/posts/{id}/comments}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     *   <li><b>Path Variable:</b> {@code id} - the ID of the post being commented on</li>
     *   <li><b>Request Payload:</b> JSON object {@code {"content": "...", "parentId": 123}} ({@code parentId} optional)</li>
     *   <li><b>Response:</b> {@code 200 OK} with created {@link Comment}, or {@code 400 Bad Request} if blocked by profanity filter</li>
     * </ul>
     *
     * @param id   the ID of the post
     * @param body map containing {@code content} and optional {@code parentId}
     * @return a {@link ResponseEntity} with the saved {@link Comment} or error details
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String content = body.get("content") != null ? body.get("content").toString() : null;
        Long parentId = null;
        if (body.get("parentId") != null) {
            try {
                parentId = Long.valueOf(body.get("parentId").toString());
            } catch (NumberFormatException ignored) {}
        }
        try {
            Comment comment = postService.addComment(id, content, parentId, email);
            return ResponseEntity.ok(comment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "flagged", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deletes a comment (and its nested replies) if the requester is the comment's author or an {@code ADMIN}.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code DELETE /api/posts/comments/{commentId}}</li>
     *   <li><b>Access Role:</b> Comment Author or {@code ADMIN}</li>
     *   <li><b>Path Variable:</b> {@code commentId} - the ID of the comment to delete</li>
     *   <li><b>Response:</b> {@code 200 OK} on deletion, or {@code 403 Forbidden} if unauthorized</li>
     * </ul>
     *
     * @param commentId the ID of the comment to delete
     * @return a {@link ResponseEntity} confirming deletion or error
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            postService.deleteComment(commentId, email);
            return ResponseEntity.ok(Map.of("message", "Comment deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}