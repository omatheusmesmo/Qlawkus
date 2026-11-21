package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import dev.omatheusmesmo.qlawkus.tools.skillhub.OutboundPolicy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

/**
 * Test-profile stand-in for {@link OutboundPolicy}: WireMock serves plain http on loopback, which the
 * production policy refuses. Private addresses stay refused; the production rules are pinned by the
 * skill-hub unit tests.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestingOutboundPolicy extends OutboundPolicy {

  public TestingOutboundPolicy() {
    super(false, true);
  }
}
