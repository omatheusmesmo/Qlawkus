package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class SoulEngine implements SystemMessageProvider {

  private static final DateTimeFormatter NOW_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm:ss zzz (XXX)", Locale.ENGLISH);

  @Override
  @Transactional
  public Optional<String> getSystemMessage(Object memoryId) {
    Soul soul = Soul.findSoul();
    if (soul == null) {
      return Optional.empty();
    }

    return Optional.of(soul.toSystemMessage() + ownerContext() + executionBias()
        + currentDateTimeContext() + voiceCapabilityContext());
  }

  private String ownerContext() {
    UserProfile profile = UserProfile.findProfile();
    if (profile == null) {
      return "";
    }
    return "\n\n---\n\n" + profile.toContextBlock();
  }

  private String executionBias() {
    return "\n\n---\n\n## Acting\n\n"
        + "Act in-turn on actionable requests: don't describe what you would do, do it. When a tool "
        + "can answer or accomplish something, call the tool — never narrate a tool action as if you "
        + "already performed it without actually calling it. Continue until the task is done or you "
        + "are genuinely blocked, then say what blocked you. If a tool result is weak or empty, try "
        + "another approach before giving up.";
  }

  private String voiceCapabilityContext() {
    boolean ttsEnabled = ConfigProvider.getConfig()
        .getOptionalValue("qlawkus.messaging.tts.enabled", Boolean.class)
        .orElse(false);
    if (!ttsEnabled) {
      return "";
    }
    return "\n\n## Voice Replies\n\n"
        + "You CAN reply with a spoken voice message. When the user asks to hear the answer "
        + "in audio/voice (\"responde em audio\", \"me manda por voz\", \"reply by voice\"), "
        + "call the respondWithVoice tool with the language code of your reply (e.g. \"pt\" or "
        + "\"en\"), then write your reply normally: it will be synthesized and delivered as audio. "
        + "Never claim you cannot produce audio.";
  }

  private String currentDateTimeContext() {
    String timezone = Arc.container().instance(AgentConfig.class).get().timezone();
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
    return "\n\n## Current Date & Time\n\n"
        + "Right now it is **" + now.format(NOW_FORMAT) + "**.\n"
        + "Always use this as the reference for relative expressions like "
        + "\"today\", \"tomorrow\", \"tonight\", \"next week\". Never guess the date.";
  }
}
