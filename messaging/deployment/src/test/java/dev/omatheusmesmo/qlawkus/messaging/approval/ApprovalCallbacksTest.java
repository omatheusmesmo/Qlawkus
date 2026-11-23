package dev.omatheusmesmo.qlawkus.messaging.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalCallbacksTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";

    @Test
    void anApproveButtonBecomesTheTypedApproveCommandFromThePressingUser() {
        Optional<MessagingMessage> command = ApprovalCallbacks.toCommand("telegram", "42", "7",
                ApprovalCallbacks.approve(ID));

        MessagingMessage message = command.orElseThrow();
        assertEquals("/approve " + ID, message.text());
        assertEquals("telegram", message.providerId());
        assertEquals("42", message.chatId());
        assertEquals("7", message.userId(), "the decision belongs to whoever pressed, not to the prompt's addressee");
        assertTrue(ApprovalCommands.isCommand(message.text()));
    }

    @Test
    void aDenyButtonBecomesTheTypedDenyCommand() {
        MessagingMessage message = ApprovalCallbacks.toCommand("discord", "9", "7", ApprovalCallbacks.deny(ID))
                .orElseThrow();

        assertEquals("/deny " + ID, message.text());
    }

    @Test
    void thePayloadFitsTelegramsSixtyFourByteLimit() {
        assertTrue(ApprovalCallbacks.approve(ID).getBytes(StandardCharsets.UTF_8).length <= 64);
        assertTrue(ApprovalCallbacks.deny(ID).getBytes(StandardCharsets.UTF_8).length <= 64);
    }

    @Test
    void aPayloadThisFeatureDidNotCreateIsIgnored() {
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", null).isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "").isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "other:approve:" + ID).isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve").isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve:" + ID + ":extra").isEmpty());
    }

    @Test
    void aForgedVerbOrIdNeverBecomesACommand() {
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:execute:" + ID).isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve:short").isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve:" + ID.toUpperCase()).isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve:" + ID + "\n/deny " + ID).isEmpty());
        assertTrue(ApprovalCallbacks.toCommand("telegram", "42", "7", "qa:approve:../../etc/passwd").isEmpty());
    }
}
