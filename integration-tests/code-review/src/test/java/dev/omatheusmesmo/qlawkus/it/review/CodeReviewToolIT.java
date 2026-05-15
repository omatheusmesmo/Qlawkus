package dev.omatheusmesmo.qlawkus.it.review;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusIntegrationTest
@DisabledOnOs(OS.WINDOWS)
class CodeReviewToolIT extends CodeReviewToolTest {
}
