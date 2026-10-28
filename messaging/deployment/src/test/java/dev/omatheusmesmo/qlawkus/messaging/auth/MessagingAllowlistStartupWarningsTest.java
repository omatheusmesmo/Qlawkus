package dev.omatheusmesmo.qlawkus.messaging.auth;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagingAllowlistStartupWarningsTest {

    private MessagingAllowlistStartupWarnings warnings(Map<String, Optional<List<String>>> allowedUsers, String... providers) {
        MessagingAuthConfig config = Mockito.mock(MessagingAuthConfig.class);
        when(config.allowedUsers()).thenReturn(allowedUsers);
        MessagingAllowlistStartupWarnings warnings = new MessagingAllowlistStartupWarnings();
        warnings.config = config;
        warnings.activeProviderIds = List.of(providers);
        return warnings;
    }

    @Test
    void missingList_warnsThatEveryMessageIsRejected() {
        assertEquals(List.of(
                "Messaging channel telegram has no allowlist: every message is rejected until qlawkus.messaging.auth.allowed-users.telegram lists a user id"),
                warnings(Map.of(), "telegram").messages());
    }

    @Test
    void emptyList_warnsThatEveryMessageIsRejected() {
        assertEquals(List.of(
                "Messaging channel discord has no allowlist: every message is rejected until qlawkus.messaging.auth.allowed-users.discord lists a user id"),
                warnings(Map.of("discord", Optional.of(List.of())), "discord").messages());
    }

    @Test
    void wildcard_warnsThatAnyoneIsAccepted() {
        assertEquals(List.of(
                "Messaging channel discord accepts messages from anyone (qlawkus.messaging.auth.allowed-users.discord=*)"),
                warnings(Map.of("discord", Optional.of(List.of("*"))), "discord").messages());
    }

    @Test
    void listedUserId_hasNoWarning() {
        assertTrue(warnings(Map.of("telegram", Optional.of(List.of("123456"))), "telegram").messages().isEmpty());
    }

    @Test
    void providerNotActive_hasNoWarningEvenWithoutList() {
        assertTrue(warnings(Map.of("slack", Optional.empty())).messages().isEmpty());
    }
}
