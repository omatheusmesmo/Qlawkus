package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the shipped redeploy hook, as a process, against this running agent. The hook is the
 * documented way to turn a staged manifest into a running instance, so the thing worth testing is
 * the script itself over real HTTP - not a reimplementation of what it is supposed to do.
 *
 * <p>Phases 3 and 4 are the caller's, so a standalone run no-ops them; what this exercises is fetch,
 * promote, the readiness gate and discard. Promotion is pointed at throwaway files so a test run
 * never writes into the source tree.
 */
@QuarkusTest
class RedeployHookTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final Path HOOK = Path.of("../../examples/redeploy/redeploy.sh");
  private static final Path WORK = Path.of("target/redeploy");

  private Path manifestTarget;
  private Path configTarget;

  @BeforeEach
  void prepare() throws IOException {
    assumeTrue(Files.isReadable(HOOK), "redeploy hook not found at " + HOOK.toAbsolutePath());
    assumeTrue(onPath("jq") && onPath("bash"), "the hook needs bash and jq");

    Files.createDirectories(WORK);
    manifestTarget = WORK.resolve("agent.yml");
    configTarget = WORK.resolve("config-overrides.properties");
    // The hook refuses to promote into a path that does not exist, so seed both targets.
    Files.writeString(manifestTarget, "version: 1\nbuild-time:\n  default: enabled\n");
    Files.writeString(configTarget, "");
    discardStaged();
  }

  /**
   * The ordinary case, and the one the guides call safe to run in CI on every deploy: nothing is
   * staged, so there is nothing to promote and the run still has to reach the end.
   */
  @Test
  void run_withNothingStaged_completes() throws Exception {
    Result result = runHook(Map.of());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("no staged manifest"), result.output());
    assertTrue(result.output().contains("instance is healthy"), result.output());
    assertTrue(result.output().contains("redeploy complete"), result.output());
  }

  /** Phase 5 is a real readiness gate: it has to be the agent answering, not just an open port. */
  @Test
  void run_pollsTheAgentsReadinessEndpoint() throws Exception {
    given().when().get("/q/health/ready").then().statusCode(200);

    Result result = runHook(Map.of());

    assertTrue(result.output().contains("/q/health/ready"), result.output());
    assertTrue(result.output().contains("instance is healthy"), result.output());
  }

  /** A staged manifest reaches the file a builder would read, byte for byte. */
  @Test
  void run_promotesAStagedManifest() throws Exception {
    String manifest = "version: 1\nbuild-time:\n  default: disabled\n  except:\n    - console\n";
    stageManifest(manifest);

    Result result = runHook(Map.of("QLAWKUS_DRY_RUN", "1"));

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("promoted staged manifest"), result.output());
    assertEquals(manifest.strip(), Files.readString(manifestTarget).strip());
  }

  /** A dry run stops after promotion, so the staged document survives for the real run. */
  @Test
  void dryRun_leavesTheStagedManifestInPlace() throws Exception {
    stageManifest("version: 1\nbuild-time:\n  default: disabled\n  except:\n    - console\n");

    runHook(Map.of("QLAWKUS_DRY_RUN", "1"));

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/composition")
        .then().statusCode(200)
        .body("staged", org.hamcrest.Matchers.notNullValue());
  }

  /** Once a real run is verified, the consumed manifest is dropped so the next deploy is a no-op. */
  @Test
  void run_discardsTheStagedManifestOnceHealthy() throws Exception {
    stageManifest("version: 1\nbuild-time:\n  default: disabled\n  except:\n    - console\n");

    Result result = runHook(Map.of());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("discarded the consumed staged manifest"), result.output());

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/composition")
        .then().statusCode(200)
        .body("staged", org.hamcrest.Matchers.nullValue());
  }

  private void stageManifest(String yaml) {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("text/plain")
        .body(yaml)
        .when().post("/api/admin/composition/manifest")
        .then().statusCode(200);
  }

  private void discardStaged() {
    given().auth().preemptive().basic(USER, PASS)
        .when().delete("/api/admin/composition/manifest")
        .then().statusCode(org.hamcrest.Matchers.anyOf(
            org.hamcrest.Matchers.is(204), org.hamcrest.Matchers.is(404)));
  }

  private Result runHook(Map<String, String> extraEnv) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder("bash", HOOK.toAbsolutePath().toString());
    builder.redirectErrorStream(true);
    Map<String, String> env = new HashMap<>(builder.environment());
    env.put("QLAWKUS_BASE_URL", "http://localhost:" + RestAssured.port);
    env.put("QLAWKUS_ADMIN_USER", USER);
    env.put("QLAWKUS_ADMIN_PASSWORD", PASS);
    env.put("QLAWKUS_SOURCE_MANIFEST", manifestTarget.toAbsolutePath().toString());
    env.put("QLAWKUS_SOURCE_CONFIG_OVERRIDES", configTarget.toAbsolutePath().toString());
    env.put("QLAWKUS_HEALTH_TIMEOUT", "30");
    env.putAll(extraEnv);
    builder.environment().clear();
    builder.environment().putAll(env);

    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(process.waitFor(90, TimeUnit.SECONDS), "the hook did not finish:\n" + output);
    return new Result(process.exitValue(), output);
  }

  private static boolean onPath(String command) {
    try {
      return new ProcessBuilder(command, "--version").redirectErrorStream(true).start()
          .waitFor(10, TimeUnit.SECONDS);
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  private record Result(int exitCode, String output) {
  }
}
