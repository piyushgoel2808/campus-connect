package com.bvicam.campusconnect.config;

import com.bvicam.campusconnect.service.CustomUserDetailsService;
import com.bvicam.campusconnect.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the STOMP inbound channel JWT authentication interceptor configured in {@link WebSocketConfig}.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    private static final String TEST_SECRET =
            "5367566B59703373367639792B423F4528482B4D6251655468576D5A71347437";

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private MessageChannel messageChannel;

    private JwtUtil jwtUtil;
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        jwtUtil.init();

        WebSocketConfig webSocketConfig = new WebSocketConfig();
        ReflectionTestUtils.setField(webSocketConfig, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(webSocketConfig, "userDetailsService", userDetailsService);

        List<ChannelInterceptor> captured = new ArrayList<>();
        ChannelRegistration registration = new ChannelRegistration() {
            @Override
            public ChannelRegistration interceptors(ChannelInterceptor... interceptors) {
                captured.addAll(Arrays.asList(interceptors));
                return super.interceptors(interceptors);
            }
        };

        webSocketConfig.configureClientInboundChannel(registration);
        assertThat(captured).hasSize(1);
        this.interceptor = captured.get(0);
    }

    @Test
    @DisplayName("STOMP CONNECT without Authorization Bearer header is rejected")
    void connect_missingAuthHeader_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing Authorization Bearer token");
    }

    @Test
    @DisplayName("STOMP CONNECT with invalid JWT token is rejected")
    void connect_invalidJwt_throwsIllegalArgumentException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer not-a-valid-jwt-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized WebSocket connection");
    }

    @Test
    @DisplayName("STOMP CONNECT with valid JWT sets authenticated user Principal on StompHeaderAccessor")
    void connect_validJwt_authenticatesStompPrincipal() {
        String token = jwtUtil.generateToken("student@bvicam.in");
        UserDetails userDetails = new User(
                "student@bvicam.in",
                "encoded-hash",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        when(userDetailsService.loadUserByUsername("student@bvicam.in")).thenReturn(userDetails);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("student@bvicam.in");
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("Unauthenticated SEND and SUBSCRIBE frames are rejected while authenticated frames pass")
    void sendAndSubscribe_requireAuthenticatedUser() {
        StompHeaderAccessor unauthSend = StompHeaderAccessor.create(StompCommand.SEND);
        unauthSend.setLeaveMutable(true);
        Message<byte[]> sendMsg = MessageBuilder.createMessage(new byte[0], unauthSend.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(sendMsg, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthenticated STOMP frame");

        StompHeaderAccessor unauthSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        unauthSub.setLeaveMutable(true);
        Message<byte[]> subMsg = MessageBuilder.createMessage(new byte[0], unauthSub.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(subMsg, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthenticated STOMP frame");

        StompHeaderAccessor authSend = StompHeaderAccessor.create(StompCommand.SEND);
        authSend.setLeaveMutable(true);
        authSend.setUser(new UsernamePasswordAuthenticationToken(
                "student@bvicam.in", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        Message<byte[]> authMsg = MessageBuilder.createMessage(new byte[0], authSend.getMessageHeaders());

        assertThat(interceptor.preSend(authMsg, messageChannel)).isSameAs(authMsg);
    }
}
