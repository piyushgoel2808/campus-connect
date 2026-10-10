package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.Comment;
import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.Role;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.CommentRepository;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for managing community feed {@link Post} entities and nested {@link Comment} threads.
 * <p>
 * Coordinates feed ordering (pinned posts first), automated profanity screening via
 * {@link ProfanityFilterService}, automatic account flagging via {@link UserModerationService},
 * post likes, admin pinning, and role/ownership-verified deletions.
 * </p>
 */
@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProfanityFilterService profanityFilterService;

    @Autowired
    private UserModerationService userModerationService;

    // =========================================================
    // 1. POST MANAGEMENT (FEED, CREATE, DELETE, LIKE, PIN)
    // =========================================================

    /**
     * Retrieves all community feed posts ordered with pinned posts at the top,
     * followed by unpinned posts in reverse chronological order (newest first).
     *
     * @return a sorted list of all {@link Post} entities
     */
    // Corrected: Single method that handles pinned posts first, then chronological order
    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByIsPinnedDescCreatedAtDesc();
    }

    /**
     * Creates and saves a new community post authored by the specified user.
     * <p>
     * Screens the post content for profanity before saving. If prohibited language is detected,
     * the author's account is immediately flagged in a separate transaction (via {@link UserModerationService})
     * and an {@link IllegalArgumentException} is thrown to reject the post.
     * </p>
     *
     * @param post  the {@link Post} payload containing text content and optional image URL
     * @param email the email address of the authenticated author
     * @return the persisted {@link Post} entity
     * @throws IllegalArgumentException if the post content contains profanity
     * @throws RuntimeException         if the author email cannot be resolved to a user
     */
    @Transactional
    public Post createPost(Post post, String email) {
        // Step 1: Screen content for prohibited or abusive language
        if (profanityFilterService.containsProfanity(post.getContent())) {
            // Flag the user in an independent transaction (REQUIRES_NEW) so the flag persists even when this transaction rolls back
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your post contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        // Step 2: Resolve the authenticated user and link as post author
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        post.setAuthor(user);

        // Step 3: Persist the post to the database
        return postRepository.save(post);
    }

    /**
     * Deletes a post if and only if the requester is either an {@code ADMIN} or the original author of the post.
     *
     * @param postId         the ID of the post to delete
     * @param requesterEmail the email address of the authenticated user requesting deletion
     * @throws RuntimeException if the post is not found or the user is unauthorized
     */
    @Transactional
    public void deletePost(Long postId, String requesterEmail) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow();

        // Safe Enum check: Admin or author can delete
        if (requester.getRole() == Role.ADMIN || post.getAuthor().getEmail().equals(requesterEmail)) {
            postRepository.delete(post);
        } else {
            throw new RuntimeException("Unauthorized to delete this post");
        }
    }

    /**
     * Increments the like counter on the specified post by 1.
     *
     * @param postId the ID of the post to like
     */
    @Transactional
    public void likePost(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (post.getLikes() == null) post.setLikes(0);
        post.setLikes(post.getLikes() + 1);
        postRepository.save(post);
    }

    /**
     * Toggles the pinned status of a post (pinning unpinned posts, or unpinning pinned posts).
     * Restricted strictly to users with the {@link Role#ADMIN} role.
     *
     * @param postId         the ID of the post to pin or unpin
     * @param requesterEmail the email address of the authenticated user
     * @throws RuntimeException if the user is not an Admin or the post does not exist
     */
    @Transactional
    public void togglePin(Long postId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Only ADMINs allowed
        if (requester.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only Admins can pin posts.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Logic: If null, treat as false, then flip it
        boolean currentStatus = post.getIsPinned() != null && post.getIsPinned();
        post.setIsPinned(!currentStatus);

        postRepository.save(post);
    }

    // =========================================================
    // 2. COMMENTS & REPLIES FUNCTIONALITY
    // =========================================================

    /**
     * Fetches all top-level comments for a given post in chronological order (oldest first).
     * Child replies are nested inside each top-level comment's {@code replies} list.
     *
     * @param postId the ID of the post
     * @return a list of top-level {@link Comment} entities for the post
     */
    public List<Comment> getComments(Long postId) {
        return commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId);
    }

    /**
     * Adds a new top-level comment or nested reply to a post after screening for profanity.
     * <p>
     * Also increments the parent post's denormalized {@code commentsCount} counter.
     * </p>
     *
     * @param postId   the ID of the post being commented on
     * @param content  the text content of the comment
     * @param parentId optional ID of the parent comment if this is a nested reply (or {@code null} for top-level)
     * @param email    the email address of the authenticated comment author
     * @return the persisted {@link Comment} entity
     * @throws IllegalArgumentException if the content is empty or contains profanity
     */
    @Transactional
    public Comment addComment(Long postId, String content, Long parentId, String email) {
        // Step 1: Validate non-empty content
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty.");
        }

        // Step 2: Check for profanity and flag the user's account if detected
        if (profanityFilterService.containsProfanity(content)) {
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your comment contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        // Step 3: Load target post and comment author
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Step 4: Construct the Comment entity
        Comment comment = new Comment();
        comment.setContent(content.trim());
        comment.setAuthor(author);
        comment.setPost(post);
        comment.setCreatedAt(LocalDateTime.now());

        // Step 5: Link to parent comment if this is a nested reply
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            comment.setParentComment(parent);
        }

        Comment saved = commentRepository.save(comment);

        // Step 6: Increment post's total comment counter
        // Update post comment count
        int currentCount = post.getCommentsCount() != null ? post.getCommentsCount() : 0;
        post.setCommentsCount(currentCount + 1);
        postRepository.save(post);

        return saved;
    }

    /**
     * Deletes a comment (and its cascaded child replies) if the requester is an {@code ADMIN}
     * or the comment's author, and decrements the parent post's {@code commentsCount} accordingly.
     *
     * @param commentId      the ID of the comment to delete
     * @param requesterEmail the email address of the authenticated requester
     * @throws RuntimeException if the comment is not found or the user is unauthorized
     */
    @Transactional
    public void deleteComment(Long commentId, String requesterEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow();

        // Safe Enum check: Admin or author can delete
        if (requester.getRole() == Role.ADMIN || comment.getAuthor().getEmail().equals(requesterEmail)) {
            Post post = comment.getPost();
            // Account for both the comment itself and any nested replies that will cascade-delete
            int deletedCount = 1 + (comment.getReplies() != null ? comment.getReplies().size() : 0);
            commentRepository.delete(comment);
            if (post != null) {
                int count = post.getCommentsCount() != null ? post.getCommentsCount() : deletedCount;
                post.setCommentsCount(Math.max(0, count - deletedCount));
                postRepository.save(post);
            }
        } else {
            throw new RuntimeException("Unauthorized to delete this comment");
        }
    }
}