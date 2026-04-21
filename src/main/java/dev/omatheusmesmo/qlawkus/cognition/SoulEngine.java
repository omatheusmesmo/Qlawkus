package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class SoulEngine implements SystemMessageProvider {

    @Override
    @Transactional
    public Optional<String> getSystemMessage(Object memoryId) {
        SoulEntity soul = SoulEntity.findSoul();
        if (soul == null) {
            return Optional.empty();
        }

        String message = buildSystemMessage(soul);
        return Optional.of(message);
    }

    String buildSystemMessage(SoulEntity soul) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are ").append(soul.name).append(".\n\n");
        sb.append(soul.coreIdentity).append("\n\n");
        sb.append("---\n\n");
        sb.append("Current state: ").append(soul.currentState).append("\n\n");
        sb.append("Current mood: ").append(soul.mood).append(" — ")
                .append(soul.mood.getDescription()).append("\n\n");
        sb.append("Adjust your approach based on your current mood ")
                .append("while staying true to your core identity.");

        return sb.toString();
    }
}
