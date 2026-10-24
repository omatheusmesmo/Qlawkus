package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
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
  private static final String REDEPLOYS = "/api/admin/composition/redeploys";

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

  /**
   * The loop reports each run to the instance it just verified, so a successful run is readable on
   * the agent afterwards with every phase timed.
   */
  @Test
  void run_reportsASuccessfulRunWithEveryPhaseTimed() throws Exception {
    Result result = runHook(Map.of());

    assertEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("reported the success run"), result.output());
    given().auth().preemptive().basic(USER, PASS)
        .when().get(REDEPLOYS)
        .then().statusCode(200)
        .body("runs[0].outcome", equalTo("success"))
        .body("runs[0].failedPhase", nullValue())
        .body("runs[0].phases.keySet()", contains("fetch", "promote", "build", "restart", "verify"))
        .body("lastSuccessAt", equalTo(latestRecordedAt()));
  }

  /**
   * A failing phase is reported as the phase that failed, with the time it ran before failing, and
   * the loop still exits with that phase's own status: reporting observes the run, it never changes
   * how the run ended.
   */
  @Test
  void aFailingPhase_isReportedAndKeepsItsExitStatus() throws Exception {
    Path caller = WORK.resolve("failing-build.sh");
    Files.writeString(caller, """
        #!/usr/bin/env bash
        set -euo pipefail
        build() { echo "simulated build failure" >&2; return 3; }
        source "%s"
        redeploy_run
        """.formatted(HOOK.toAbsolutePath()));

    Result result = runScript(caller, Map.of());

    assertEquals(3, result.exitCode(), result.output());
    assertTrue(result.output().contains("reported the failure run"), result.output());
    given().auth().preemptive().basic(USER, PASS)
        .when().get(REDEPLOYS)
        .then().statusCode(200)
        .body("runs[0].outcome", equalTo("failure"))
        .body("runs[0].failedPhase", equalTo("build"))
        .body("runs[0].phases.keySet()", contains("fetch", "promote", "build"))
        .body("runs[0].message", containsString("build"));
  }

  /**
   * When no instance answers, the report is written to the loop's own log and the run fails exactly
   * as it would have without reporting.
   */
  @Test
  void anUnreachableInstance_isLoggedLocallyAndChangesNothing() throws Exception {
    Result result = runHook(Map.of(
        "QLAWKUS_BASE_URL", "http://127.0.0.1:9",
        "QLAWKUS_REPORT_TIMEOUT", "2"));

    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("could not report the failure run"), result.output());
    assertTrue(result.output().contains("\"failedPhase\":\"fetch\""), result.output());
  }

  /** A dry run changes nothing, so it is not a redeploy and leaves no record. */
  @Test
  void dryRun_reportsNothing() throws Exception {
    runHook(Map.of());
    String before = latestRecordedAt();

    Result result = runHook(Map.of("QLAWKUS_DRY_RUN", "1"));

    assertEquals(0, result.exitCode(), result.output());
    assertEquals(before, latestRecordedAt(), "a dry run must not be recorded");
  }

  private String latestRecordedAt() {
    return given().auth().preemptive().basic(USER, PASS)
        .when().get(REDEPLOYS)
        .then().statusCode(200)
        .extract().path("runs[0].recordedAt");
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
    return runScript(HOOK, extraEnv);
  }

  private Result runScript(Path script, Map<String, String> extraEnv)
      throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder("bash", script.toAbsolutePath().toString());
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
