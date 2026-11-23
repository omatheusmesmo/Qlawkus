package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalResumePromptTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void anExecutedOutcomeCarriesTheBoundedResultAsDataAndNeverAnInvitationToRepeatIt() {
        String prompt = ApprovalResumePrompt.render(request(ApprovalStatus.EXECUTED, "sent to ana"));

        assertTrue(prompt.contains("EXECUTED"));
        assertTrue(prompt.contains("sent to ana"));
        assertTrue(prompt.contains("Do not run it again"));
    }

    @Test
    void aDeniedOutcomeSaysTheActionDidNotRun() {
        String prompt = ApprovalResumePrompt.render(request(ApprovalStatus.DENIED, null));

        assertTrue(prompt.contains("denied"));
        assertTrue(prompt.contains("did not run"));
    }

    @Test
    void aFailedOutcomeSaysItDidNotComplete() {
        String prompt = ApprovalResumePrompt.render(request(ApprovalStatus.FAILED, "boom"));

        assertTrue(prompt.contains("FAILED"));
        assertTrue(prompt.contains("boom"));
        assertFalse(prompt.contains("completed successfully"));
    }

    private static ApprovalRequest request(ApprovalStatus status, String summary) {
        return new ApprovalRequest("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sendEmail", "{}", "hash", "sendEmail {}",
                "telegram:42", "telegram", "42", status, NOW, NOW, NOW, "owner", summary);
    }
}
