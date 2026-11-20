package dev.omatheusmesmo.qlawkus.tools.skillhub;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

/**
 * Test-profile stand-in for {@link OutboundPolicy}: WireMock listens on plain http and loopback, so the
 * CDI-wired hub in the {@code @QuarkusTest} classes must accept both. Private targets stay refused, and
 * the production rules are pinned by {@code OutboundPolicyTest} and the hub tests that build a strict
 * policy directly.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestingOutboundPolicy extends OutboundPolicy {

  public TestingOutboundPolicy() {
    super(false, true);
  }
}
