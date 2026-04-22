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
    Soul soul = Soul.findSoul();
    if (soul == null) {
      return Optional.empty();
    }
    return Optional.of(soul.toSystemMessage());
  }
}
