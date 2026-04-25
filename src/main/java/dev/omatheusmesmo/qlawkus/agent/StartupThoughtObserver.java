package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.Soul;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
class StartupThoughtObserver {

  @Inject
  AgentService agentService;

  @ConfigProperty(name = "qlawkus.startup-thought.enabled", defaultValue = "true")
  boolean startupThoughtEnabled;

  void onStartup(@Observes StartupEvent event) {
    logSoulState();
    if (startupThoughtEnabled) {
      generateStartupThought();
    }
  }

  @Transactional
  void logSoulState() {
    Soul soul = Soul.findSoul();
    if (soul != null) {
      Log.infof("Soul initialized: %s [%s] — %s", soul.name, soul.mood, soul.currentState);
    } else {
      Log.warn("No Soul found in database at startup");
    }
  }

  void generateStartupThought() {
    try {
      String thought = agentService.chat(
        "You just initialized. Briefly reflect on your current state in one sentence."
      )
      .collect()
      .in(StringBuilder::new, StringBuilder::append)
      .await()
      .atMost(java.time.Duration.ofSeconds(300))
      .toString();

      Log.infof("Thought ▸ Startup reflection: %s", thought.trim());
    } catch (Exception e) {
      Log.warnf("Could not generate startup thought: %s", e.getMessage());
    }
  }
}
