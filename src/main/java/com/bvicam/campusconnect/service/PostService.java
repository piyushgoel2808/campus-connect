package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.CreatePostRequest;
import com.bvicam.campusconnect.entity.Comment;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.PostLike;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.CommentRepository;
import com.bvicam.campusconnect.repository.PostLikeRepository;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service layer for managing community feed {@link Post} entities, per-user likes, and nested {@link Comment} threads.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProfanityFilterService profanityFilterService;

    @Autowired
    private UserModerationService userModerationService;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByIsPinnedDescCreatedAtDesc();
    }

    public List<Post> getPostsPage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return postRepository.findAllByOrderByIsPinnedDescCreatedAtDesc(PageRequest.of(safePage, safeSize)).getContent();
    }

    @Transactional
    public Post createPost(CreatePostRequest request, String email) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Post content cannot be empty.");
        }
        String content = request.getContent().trim();
        if (content.length() > 5000) {
            throw new IllegalArgumentException("Post content exceeds maximum length of 5000 characters.");
        }

        if (profanityFilterService.containsProfanity(content)) {
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your post contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Post post = new Post();
        post.setContent(content);
        post.setImageUrl(sanitizeImageUrl(request.getImageUrl()));
        post.setAuthor(user);
        post.setLikes(0);
        post.setIsPinned(false);
        post.setCommentsCount(0);

        return postRepository.save(post);
    }

    private String sanitizeImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("/uploads/posts/")) {
            return trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (IllegalArgumentException ignored) {
        }
        throw new IllegalArgumentException("Image URL must be an uploaded media path or an http/https URL.");
    }

    @Transactional
    public void deletePost(Long postId, String requesterEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isAuthor = post.getAuthor() != null && requesterEmail.equalsIgnoreCase(post.getAuthor().getEmail());

        if (!isAdmin && !isAuthor) {
            throw new SecurityException("Unauthorized to delete this post");
        }

        String imageUrl = post.getImageUrl();
        postLikeRepository.deleteByPostId(postId);
        postRepository.delete(post);
        deleteUploadedPostImageIfLocal(imageUrl);
    }

    private void deleteUploadedPostImageIfLocal(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/posts/")) {
            return;
        }
        try {
            String filename = imageUrl.substring("/uploads/posts/".length());
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return;
            }
            Path postsRoot = Paths.get(uploadDir, "posts").toAbsolutePath().normalize();
            Path target = postsRoot.resolve(filename).normalize();
            if (target.startsWith(postsRoot)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception e) {
            log.warn("Failed to remove uploaded post image '{}': {}", imageUrl, e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> toggleLike(Long postId, String userEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndUserId(postId, user.getId());
        int currentLikes = post.getLikes() != null ? post.getLikes() : 0;
        boolean liked;
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikes(Math.max(0, currentLikes - 1));
            liked = false;
        } else {
            postLikeRepository.save(new PostLike(post, user));
            post.setLikes(currentLikes + 1);
            liked = true;
        }
        postRepository.save(post);
        return Map.of("likes", post.getLikes(), "liked", liked);
    }

    @Transactional
    public boolean togglePin(Long postId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (requester.getRole() != Role.ADMIN) {
            throw new SecurityException("Only Admins can pin posts.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        boolean nextStatus = !(post.getIsPinned() != null && post.getIsPinned());
        post.setIsPinned(nextStatus);
        postRepository.save(post);
        return nextStatus;
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(Long postId) {
        List<Comment> topLevel = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId);
        initializeCommentReplies(topLevel);
        return topLevel;
    }

    private void initializeCommentReplies(List<Comment> comments) {
        if (comments == null) {
            return;
        }
        for (Comment comment : comments) {
            List<Comment> replies = comment.getReplies();
            if (replies != null && !replies.isEmpty()) {
                initializeCommentReplies(replies);
            }
        }
    }

    @Transactional
    public Comment addComment(Long postId, String content, Long parentId, String email) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Comment exceeds maximum length of 2000 characters.");
        }

        if (profanityFilterService.containsProfanity(trimmed)) {
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your comment contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Comment comment = new Comment();
        comment.setContent(trimmed);
        comment.setAuthor(author);
        comment.setPost(post);
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new NoSuchElementException("Parent comment not found"));
            if (parent.getPost() == null || !postId.equals(parent.getPost().getId())) {
                throw new IllegalArgumentException("Parent comment does not belong to the specified post.");
            }
            comment.setParentComment(parent);
        }

        Comment saved = commentRepository.save(comment);

        int currentCount = post.getCommentsCount() != null ? post.getCommentsCount() : 0;
        post.setCommentsCount(currentCount + 1);
        postRepository.save(post);

        return saved;
    }

    @Transactional
    public int deleteComment(Long commentId, String requesterEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found"));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isAuthor = comment.getAuthor() != null && requesterEmail.equalsIgnoreCase(comment.getAuthor().getEmail());

        if (!isAdmin && !isAuthor) {
            throw new SecurityException("Unauthorized to delete this comment");
        }

        Post post = comment.getPost();
        int deletedCount = countCommentSubtree(comment);
        commentRepository.delete(comment);
        if (post != null) {
            int count = post.getCommentsCount() != null ? post.getCommentsCount() : deletedCount;
            int updatedCount = Math.max(0, count - deletedCount);
            post.setCommentsCount(updatedCount);
            postRepository.save(post);
            return updatedCount;
        }
        return 0;
    }

    private int countCommentSubtree(Comment comment) {
        int total = 1;
        if (comment.getReplies() != null) {
            for (Comment reply : comment.getReplies()) {
                total += countCommentSubtree(reply);
            }
        }
        return total;
    }
}
