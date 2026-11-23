package dev.omatheusmesmo.qlawkus.tools.webfetch;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;
import java.util.List;

/**
 * Test-profile stand-in for {@link FetchPolicy}: the in-process test server listens on loopback, so the
 * CDI-wired tool must reach it. Every other private range, the blocked domains and the scheme rules
 * stay in force, and the production rules are pinned by {@code FetchPolicyTest}.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestingFetchPolicy extends FetchPolicy {

    public TestingFetchPolicy() {
        super(List.of("blocked.example"), true);
    }
}
