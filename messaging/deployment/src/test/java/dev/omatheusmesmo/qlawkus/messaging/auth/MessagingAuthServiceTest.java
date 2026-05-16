package dev.omatheusmesmo.qlawkus.messaging.auth;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagingAuthServiceTest {

    private MessagingAuthService authService;
    private MessagingAuthConfig config;

    @BeforeEach
    void setUp() {
        authService = new MessagingAuthService();
        config = Mockito.mock(MessagingAuthConfig.class);
        authService.config = config;
    }

    @Test
    void isAuthorized_allowedUser_returnsTrue() {
        when(config.allowedUsers()).thenReturn(Map.of("telegram", List.of("123456")));
        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "123456", "hello");

        assertTrue(authService.isAuthorized(msg));
    }

    @Test
    void isAuthorized_unknownUser_returnsFalse() {
        when(config.allowedUsers()).thenReturn(Map.of("telegram", List.of("123456")));
        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "999999", "hello");

        assertFalse(authService.isAuthorized(msg));
    }

    @Test
    void isAuthorized_noAllowlistForProvider_returnsFalse() {
        when(config.allowedUsers()).thenReturn(Map.of());
        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "123456", "hello");

        assertFalse(authService.isAuthorized(msg));
    }

    @Test
    void isAuthorized_multipleAllowedUsers_correctlyMatches() {
        when(config.allowedUsers()).thenReturn(
                Map.of("discord", List.of("user-a", "user-b", "user-c")));
        MessagingMessage allowed = MessagingMessage.text("discord", "ch", "user-b", "hi");
        MessagingMessage denied = MessagingMessage.text("discord", "ch", "user-x", "hi");

        assertTrue(authService.isAuthorized(allowed));
        assertFalse(authService.isAuthorized(denied));
    }

    @Test
    void isAuthorized_providerNotConfigured_deniesEvenIfUserIdMatches() {
        when(config.allowedUsers()).thenReturn(Map.of("telegram", List.of("123456")));
        MessagingMessage msg = MessagingMessage.text("slack", "ch", "123456", "hi");

        assertFalse(authService.isAuthorized(msg));
    }

    @Test
    void isAuthorized_wildcardEntry_allowsAnyUser() {
        when(config.allowedUsers()).thenReturn(Map.of("discord", List.of("*")));
        MessagingMessage anyUser = MessagingMessage.text("discord", "ch", "random-user-id", "hi");

        assertTrue(authService.isAuthorized(anyUser));
    }
}
