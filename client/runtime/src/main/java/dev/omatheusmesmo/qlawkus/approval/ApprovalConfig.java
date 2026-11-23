package dev.omatheusmesmo.qlawkus.approval;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Runtime configuration for the owner approval gate. The gate is opt-in: with no tool named in
 * {@link #tools()} every tool runs exactly as it did before the gate existed.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.approval")
public interface ApprovalConfig {

    /**
     * Names of {@code @QlawTool} tools whose calls wait for the owner's approval before they run.
     * Matching is exact, on the tool's name as the model sees it. Tools declared on the AI service
     * itself are not gated.
     */
    Optional<List<String>> tools();

    /**
     * How long a pending request stays decidable. After this the request expires and the action never
     * runs, however late the owner answers.
     */
    @WithDefault("24h")
    Duration expiry();

    /**
     * Most requests that may wait for the owner at once. A call beyond this is refused rather than
     * queued, so a looping model cannot bury the owner in requests.
     */
    @WithDefault("50")
    int maxPending();

    /**
     * Longest argument text shown in a request's preview. The full arguments are kept in the record for
     * inspection and are what the approved call runs; only the preview sent to the chat is shortened.
     */
    @WithDefault("500")
    int previewMaxChars();
}
