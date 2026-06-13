package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
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

  private static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

  @Override
  @Transactional
  public Optional<String> getSystemMessage(Object memoryId) {
    Soul soul = Soul.findSoul();
    if (soul == null) {
      return Optional.empty();
    }

    return Optional.of(soul.toSystemMessage() + ownerContext()
        + currentDateTimeContext() + voiceCapabilityContext());
  }

  private String ownerContext() {
    UserProfile profile = UserProfile.findProfile();
    if (profile == null) {
      return "";
    }
    return "\n\n---\n\n" + profile.toContextBlock();
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
    String timezone = ConfigProvider.getConfig()
        .getOptionalValue("qlawkus.agent.timezone", String.class)
        .orElse(DEFAULT_TIMEZONE);
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
    return "\n\n## Current Date & Time\n\n"
        + "Right now it is **" + now.format(NOW_FORMAT) + "**.\n"
        + "Always use this as the reference for relative expressions like "
        + "\"today\", \"tomorrow\", \"tonight\", \"next week\". Never guess the date.";
  }
}
