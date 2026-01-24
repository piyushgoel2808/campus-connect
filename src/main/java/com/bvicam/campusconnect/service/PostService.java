package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.Post;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.PostRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    // Corrected: Single method that handles pinned posts first, then chronological order
    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByIsPinnedDescCreatedAtDesc();
    }

    @Transactional
    public Post createPost(Post post, String email) {
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

        // Safe comparison: "ADMIN".equals(...) avoids NullPointerException
        if ("ADMIN".equals(requester.getRole()) || post.getAuthor().getEmail().equals(requesterEmail)) {
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
        if (!"ADMIN".equals(requester.getRole())) {
            throw new RuntimeException("Only Admins can pin posts.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Logic: If null, treat as false, then flip it
        boolean currentStatus = post.getIsPinned() != null && post.getIsPinned();
        post.setIsPinned(!currentStatus);

        postRepository.save(post);
    }
}