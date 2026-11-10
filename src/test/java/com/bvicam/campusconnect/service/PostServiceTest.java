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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PostService} covering idempotent likes, comment integrity, pin RBAC, and URL sanitization.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ProfanityFilterService profanityFilterService;

    @Mock
    private UserModerationService userModerationService;

    @InjectMocks
    private PostService postService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "uploadDir", "target/test-uploads");
    }

    @Test
    @DisplayName("toggleLike records a PostLike on first call and removes it on second call")
    void toggleLike_togglesBetweenLikedAndUnliked() {
        User user = new User();
        user.setId(10L);
        user.setEmail("student@bvicam.in");

        Post post = new Post();
        post.setId(1L);
        post.setLikes(0);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByEmail("student@bvicam.in")).thenReturn(Optional.of(user));
        when(postLikeRepository.findByPostIdAndUserId(1L, 10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new PostLike(post, user)));

        Map<String, Object> first = postService.toggleLike(1L, "student@bvicam.in");
        assertThat(first.get("liked")).isEqualTo(true);
        assertThat(first.get("likes")).isEqualTo(1);
        verify(postLikeRepository).save(any(PostLike.class));

        Map<String, Object> second = postService.toggleLike(1L, "student@bvicam.in");
        assertThat(second.get("liked")).isEqualTo(false);
        assertThat(second.get("likes")).isEqualTo(0);
        verify(postLikeRepository).delete(any(PostLike.class));
    }

    @Test
    @DisplayName("addComment rejects replies whose parent comment belongs to a different post")
    void addComment_rejectsParentCommentFromDifferentPost() {
        User author = new User();
        author.setId(10L);
        author.setEmail("student@bvicam.in");

        Post targetPost = new Post();
        targetPost.setId(1L);

        Post otherPost = new Post();
        otherPost.setId(2L);

        Comment foreignParent = new Comment();
        foreignParent.setId(99L);
        foreignParent.setPost(otherPost);

        when(profanityFilterService.containsProfanity("Nice post!")).thenReturn(false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(targetPost));
        when(userRepository.findByEmail("student@bvicam.in")).thenReturn(Optional.of(author));
        when(commentRepository.findById(99L)).thenReturn(Optional.of(foreignParent));

        assertThatThrownBy(() -> postService.addComment(1L, "Nice post!", 99L, "student@bvicam.in"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    @DisplayName("deleteComment decrements post commentsCount by the entire deleted subtree size")
    void deleteComment_decrementsCountBySubtreeSize() {
        User author = new User();
        author.setId(10L);
        author.setEmail("student@bvicam.in");
        author.setRole(Role.STUDENT);

        Post post = new Post();
        post.setId(1L);
        post.setCommentsCount(4);

        Comment grandchild = new Comment();
        grandchild.setId(102L);
        grandchild.setPost(post);

        Comment child = new Comment();
        child.setId(101L);
        child.setPost(post);
        child.setReplies(List.of(grandchild));

        Comment root = new Comment();
        root.setId(100L);
        root.setAuthor(author);
        root.setPost(post);
        root.setReplies(List.of(child));

        when(commentRepository.findById(100L)).thenReturn(Optional.of(root));
        when(userRepository.findByEmail("student@bvicam.in")).thenReturn(Optional.of(author));

        int remaining = postService.deleteComment(100L, "student@bvicam.in");

        assertThat(remaining).isEqualTo(1);
        assertThat(post.getCommentsCount()).isEqualTo(1);
        verify(commentRepository).delete(root);
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("togglePin forbids non-ADMIN users and toggles pin state for ADMIN users")
    void togglePin_enforcesAdminRole() {
        User student = new User();
        student.setEmail("student@bvicam.in");
        student.setRole(Role.STUDENT);
        when(userRepository.findByEmail("student@bvicam.in")).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> postService.togglePin(1L, "student@bvicam.in"))
                .isInstanceOf(SecurityException.class);

        User admin = new User();
        admin.setEmail("admin@bvicam.in");
        admin.setRole(Role.ADMIN);

        Post post = new Post();
        post.setId(1L);
        post.setIsPinned(false);

        when(userRepository.findByEmail("admin@bvicam.in")).thenReturn(Optional.of(admin));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        boolean pinned = postService.togglePin(1L, "admin@bvicam.in");
        assertThat(pinned).isTrue();
        assertThat(post.getIsPinned()).isTrue();
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("createPost rejects javascript: image URLs")
    void createPost_rejectsUnsafeImageUrl() {
        CreatePostRequest req = new CreatePostRequest();
        req.setContent("Look at this link");
        req.setImageUrl("javascript:alert(1)");

        User student = new User();
        student.setEmail("student@bvicam.in");

        when(profanityFilterService.containsProfanity("Look at this link")).thenReturn(false);
        when(userRepository.findByEmail("student@bvicam.in")).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> postService.createPost(req, "student@bvicam.in"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
    }

    @Test
    @DisplayName("deletePost blocks unrelated users and cleans up PostLike rows for the author")
    void deletePost_enforcesAuthorizationAndCleansLikes() {
        User author = new User();
        author.setId(10L);
        author.setEmail("author@bvicam.in");
        author.setRole(Role.STUDENT);

        User intruder = new User();
        intruder.setId(20L);
        intruder.setEmail("intruder@bvicam.in");
        intruder.setRole(Role.STUDENT);

        Post post = new Post();
        post.setId(5L);
        post.setAuthor(author);

        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(userRepository.findByEmail("intruder@bvicam.in")).thenReturn(Optional.of(intruder));

        assertThatThrownBy(() -> postService.deletePost(5L, "intruder@bvicam.in"))
                .isInstanceOf(SecurityException.class);

        when(userRepository.findByEmail("author@bvicam.in")).thenReturn(Optional.of(author));
        postService.deletePost(5L, "author@bvicam.in");

        verify(postLikeRepository).deleteByPostId(5L);
        verify(postRepository).delete(post);
    }
}
