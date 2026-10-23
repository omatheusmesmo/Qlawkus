package dev.omatheusmesmo.qlawkus.tools.selfobserve.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build steps for the optional {@code qlawkus-self-observe} extension: read-only tools that query the
 * agent's own telemetry backends (Prometheus, Tempo, Loki).
 * <p>
 * {@code TelemetryHttp} and {@code SelfObserveTool} ({@code @QlawTool}) carry CDI scopes and are
 * discovered from this extension's Jandex index; the tool is registered as an unremovable bean and
 * for reflection by {@code ClientProcessor}'s {@code @QlawTool} scan. The tools return plain strings,
 * so no type crosses the tool boundary that would need its own reflection registration.
 */
class SelfObserveProcessor {

  private static final String FEATURE = "qlawkus-self-observe";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }
}
