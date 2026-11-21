package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One language policy for all channels. The bounded cache contains codes only, never utterances. */
@ApplicationScoped
public class ReplyLanguageResolver {
    private static final int MAX_CONVERSATIONS = 256;
    private final ReplyLanguageDecision decision;
    private final Map<String, String> stable = new LinkedHashMap<>(16, 0.75f, true);

    @Inject
    public ReplyLanguageResolver(ReplyLanguageDecision decision) {
        this.decision = decision;
    }

    public AgentReplyStyle resolve(String conversation, String message, AgentReplyStyle requested) {
        return resolve(conversation, message, requested, null, List.of(), null);
    }

    public AgentReplyStyle resolve(String conversation, String message, AgentReplyStyle requested,
            String recognizerHint, List<String> choices, String fallback) {
        if (requested.language() != null) {
            remember(conversation, requested.language());
            return requested;
        }
        String previous;
        synchronized (stable) { previous = stable.get(conversation); }
        String selected = null;
        try {
            selected = normalize(decision.decide(message, normalize(recognizerHint), previous, choices, fallback));
        } catch (RuntimeException failure) {
            Log.debug("Reply language decision unavailable; retaining the shared prompt policy");
        }
        if (selected == null || !choices.isEmpty() && !choices.contains(selected)) {
            selected = normalize(recognizerHint);
            if (selected == null) selected = normalize(fallback);
            if (selected != null && !choices.isEmpty() && !choices.contains(selected)) selected = null;
        }
        // An unknown decision never forces a stale language onto a new, differently worded request.
        if (selected != null) remember(conversation, selected);
        return new AgentReplyStyle(requested.format(), selected);
    }

    public void forget(String conversation) {
        synchronized (stable) { stable.remove(conversation); }
    }

    private void remember(String conversation, String language) {
        if (conversation == null) return;
        synchronized (stable) {
            stable.put(conversation, language);
            if (stable.size() > MAX_CONVERSATIONS) stable.remove(stable.keySet().iterator().next());
        }
    }

    private static String normalize(String code) {
        if (code == null || code.length() > 35) return null;
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*")) return null;
        int separator = normalized.indexOf('-');
        if (separator < 0) separator = normalized.indexOf('_');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }
}
