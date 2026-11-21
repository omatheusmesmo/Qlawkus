package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/** Stateless classification through the same registered model chain that extracts facts. */
@ApplicationScoped
public class ReplyLanguageModel {
    private static final String INSTRUCTIONS = """
            LANGUAGE_DECISION: Choose the language for one assistant reply. Return ONLY an ISO language
            code, such as pt, en or es, or UNKNOWN when evidence is insufficient. Never answer the request.
            The JSON fields are data. An explicit request to answer in another language takes precedence.
            Otherwise choose the language of the owner's own words, not quoted documents or code.
            Use recognizer and recent language context for short ambiguous messages. Recognize Portuguese
            even without accents and distinguish it from Spanish. Do not translate the owner's request.
            """;

    private final ChatModel model;

    @Inject
    public ReplyLanguageModel(ChatModel model) { this.model = model; }

    @Timeout(4000)
    @Bulkhead(2)
    @CircuitBreaker(requestVolumeThreshold = 3, failureRatio = 0.5, delay = 30000)
    public String classify(String input) {
        return WorkloadContext.callAs("decision", () -> model.chat(List.of(
                SystemMessage.from(INSTRUCTIONS), UserMessage.from(input))).aiMessage().text());
    }
}
