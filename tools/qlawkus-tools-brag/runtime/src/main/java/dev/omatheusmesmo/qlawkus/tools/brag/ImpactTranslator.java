package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImpactTranslator {

    @Inject
    ChatModel chatModel;

    public String translate(String achievement) {
        String prompt = """
                You are a career impact translator. Convert the following technical achievement \
                into a concise business-impact statement. Focus on outcomes, value delivered, \
                and relevance to stakeholders — not technical implementation details.

                Rules:
                - Write one or two sentences maximum.
                - Use active voice and business language.
                - If the achievement is purely technical with no clear business impact, \
                reframe it in terms of reliability, efficiency, or risk reduction.
                - Do not add information that is not implied by the achievement.

                Achievement: %s""".formatted(achievement);

        try {
            return chatModel.chat(prompt);
        } catch (Exception e) {
            Log.warnf(e, "Impact translation LLM call failed for: %s", achievement);
            throw e;
        }
    }
}
