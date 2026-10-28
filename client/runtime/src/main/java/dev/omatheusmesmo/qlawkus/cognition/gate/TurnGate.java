package dev.omatheusmesmo.qlawkus.cognition.gate;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * Decides whether a finished turn is worth an expensive post-turn generation, such as mining it for
 * facts, procedures or achievements. Each such generation costs a full model call, and most turns
 * conclude that there is nothing to keep, so a gate lets the common case stop before the call.
 *
 * <p>Implementations are consulted on the batch workload, before the generation, and must answer
 * quickly: the answer is on the path of background work, not of the owner's reply. An implementation
 * may throw; {@link TurnGates} treats a throw as a yes, so a broken gate costs only the saving it was
 * meant to make.
 */
public interface TurnGate {

  /**
   * @param question the yes/no question the caller is asking about this turn
   * @param messages the conversation of the finished turn
   * @return {@code true} to run the generation, {@code false} to skip it
   */
  boolean worthIt(TurnQuestion question, List<ChatMessage> messages);
}
