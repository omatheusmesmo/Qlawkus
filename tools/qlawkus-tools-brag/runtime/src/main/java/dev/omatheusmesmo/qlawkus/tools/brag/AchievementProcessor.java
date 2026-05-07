package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.cognition.ChatCompletedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;

@ApplicationScoped
public class AchievementProcessor {

    @Inject
    ChatModel chatModel;

    @Inject
    BragConfig config;

    void onChatCompleted(@ObservesAsync ChatCompletedEvent event) {
        if (!config.enabled() || !config.impactTranslationEnabled()) return;
        if (event.messages().isEmpty()) return;

        extractAndStore(event.messages());
    }

    void extractAndStore(Iterable<ChatMessage> messages) {
        String achievement = detectAchievement(messages);
        if (achievement == null || achievement.isBlank()) return;

        persistAchievement(achievement);
    }

    String detectAchievement(Iterable<ChatMessage> messages) {
        StringBuilder conversation = new StringBuilder();
        for (ChatMessage m : messages) {
            conversation.append(m.type().name()).append(": ").append(m).append("\n");
        }

        String prompt = """
                Analyze this conversation and determine if the user completed a meaningful \
                engineering task. An engineering task is something like: fixing a bug, adding \
                a feature, refactoring code, improving performance, writing tests, deploying \
                a service, or solving a technical problem.

                If an engineering task was completed, return a single concise sentence \
                describing what was accomplished. Use active voice and focus on the outcome.

                If no engineering task was completed, return nothing (empty response).

                Conversation:
                %s""".formatted(conversation);

        try {
            String response = chatModel.chat(prompt);
            if (response == null || response.isBlank() || response.strip().equalsIgnoreCase("none")) {
                return null;
            }
            return response.strip();
        } catch (Exception e) {
            Log.warnf(e, "Achievement detection LLM call failed");
            return null;
        }
    }

    @Transactional
    void persistAchievement(String achievement) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BragEntry existing = BragEntry.findDuplicate(today, achievement, null);
        if (existing != null) {
            Log.debugf("Duplicate achievement skipped: %s", achievement);
            return;
        }

        BragEntry entry = new BragEntry();
        entry.date = today;
        entry.achievement = achievement;
        entry.deleted = false;

        try {
            String impact = translateImpact(achievement);
            if (impact != null && !impact.isBlank()) {
                entry.impact = impact;
            }
        } catch (Exception e) {
            Log.warnf(e, "Impact translation failed for auto-detected achievement");
        }

        entry.persist();
        Log.infof("Auto-detected achievement recorded: %s", achievement);
    }

    String translateImpact(String achievement) {
        String prompt = """
                Convert this technical achievement into a concise business-impact statement. \
                Focus on outcomes, value delivered, and relevance to stakeholders.

                Rules:
                - Write one or two sentences maximum.
                - Use active voice and business language.
                - If the achievement is purely technical with no clear business impact, \
                reframe it in terms of reliability, efficiency, or risk reduction.
                - Do not add information that is not implied by the achievement.

                Achievement: %s""".formatted(achievement);

        return chatModel.chat(prompt);
    }
}
