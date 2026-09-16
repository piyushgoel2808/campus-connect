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

    // Corrected: Single method that handles pinned posts first, then chronological order
    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByIsPinnedDescCreatedAtDesc();
    }

    @Transactional
    public Post createPost(Post post, String email) {
        if (profanityFilterService.containsProfanity(post.getContent())) {
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your post contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        post.setAuthor(user);
        return postRepository.save(post);
    }

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

    @Transactional
    public void likePost(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();
        if (post.getLikes() == null) post.setLikes(0);
        post.setLikes(post.getLikes() + 1);
        postRepository.save(post);
    }

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

    // --- COMMENTS & REPLIES FUNCTIONALITY ---

    public List<Comment> getComments(Long postId) {
        return commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public Comment addComment(Long postId, String content, Long parentId, String email) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty.");
        }

        if (profanityFilterService.containsProfanity(content)) {
            userModerationService.flagUser(email);
            throw new IllegalArgumentException("Your comment contains prohibited or abusive language and has been blocked. Your account has been flagged.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Comment comment = new Comment();
        comment.setContent(content.trim());
        comment.setAuthor(author);
        comment.setPost(post);
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            comment.setParentComment(parent);
        }

        Comment saved = commentRepository.save(comment);

        // Update post comment count
        int currentCount = post.getCommentsCount() != null ? post.getCommentsCount() : 0;
        post.setCommentsCount(currentCount + 1);
        postRepository.save(post);

        return saved;
    }

    @Transactional
    public void deleteComment(Long commentId, String requesterEmail) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow();

        // Safe Enum check: Admin or author can delete
        if (requester.getRole() == Role.ADMIN || comment.getAuthor().getEmail().equals(requesterEmail)) {
            Post post = comment.getPost();
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