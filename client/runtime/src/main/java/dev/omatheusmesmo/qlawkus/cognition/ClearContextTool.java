package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClearContextTool {

    @Inject
    ConversationControl conversationControl;

    @Tool("""
            Clear the current conversation history (short-term context) when the user asks to \
            forget the conversation, reset, start over, or switch to a brand new topic. Trigger \
            on intent in ANY language, e.g. Portuguese ("esquece isso", "limpa o contexto", \
            "vamos recomeçar", "novo assunto") or English ("clear the context", "reset", \
            "start over", "new topic", "forget our conversation"). This does NOT erase long-term \
            saved facts about the user, only the running back-and-forth. The reset takes effect on \
            the next message. Confirm briefly to the user that the context was cleared.""")
    public String clearContext() {
        conversationControl.requestClear();
        Log.info("ClearContextTool: conversation clear requested");
        return "Conversation context will be cleared; the next message starts fresh.";
    }
}
