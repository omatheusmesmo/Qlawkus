package dev.omatheusmesmo.qlawkus.console.voice;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Runtime configuration of the live voice session on the console. Owned by the optional
 * {@code qlawkus-console-voice} extension; absent from a distribution that does not ship it.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.console.voice")
public interface ConsoleVoiceConfig {

    /**
     * How long one live voice session may stay open. When it elapses the server says so and closes the
     * connection, so an open microphone never runs unbounded.
     */
    @WithDefault("PT15M")
    Duration maxSessionDuration();

    /**
     * Longest single utterance the browser may send. Audio beyond this is discarded and the owner has
     * to speak again. The effective limit is the smaller of this and {@code max-utterance-bytes}.
     */
    @WithDefault("PT30S")
    Duration maxUtteranceDuration();

    /**
     * Largest single utterance, in bytes of 16-bit mono PCM, the server buffers before transcribing.
     */
    @WithDefault("4194304")
    long maxUtteranceBytes();

    /**
     * Largest binary frame the browser may send. A bigger frame is refused and the utterance it belongs
     * to is not extended.
     */
    @WithDefault("65536")
    int maxFrameBytes();

    /**
     * Reply sentences waiting for speech synthesis in one turn. A sentence beyond this is not spoken and
     * the owner is told the speech queue is full.
     */
    @WithDefault("8")
    int maxQueuedSegments();

    /**
     * Live voice sessions one owner may hold open at once. A second session is refused until the first
     * one closes.
     */
    @WithDefault("1")
    int maxSessionsPerOwner();

    /**
     * Language of the live session. {@code auto} detects the language of each utterance and of each reply,
     * falling back to the language the session has settled on and then to the text-to-speech default. An
     * ISO 639-1 code, such as {@code pt}, forces that language for speech recognition and for speech.
     */
    @WithDefault("auto")
    String language();

    /**
     * Longest typed message the owner may send from the text box. A longer message is refused.
     */
    @WithDefault("2000")
    int maxTextChars();

    /**
     * Quiet time after the last speech frame before the utterance is considered finished.
     */
    @WithDefault("PT0.8S")
    Duration hangover();

    /**
     * Root-mean-square level of a frame, between 0 and 1, above which the browser counts it as speech.
     */
    @WithDefault("0.02")
    double speechThreshold();

    /**
     * Origins allowed to open a session in addition to the page's own origin. Empty by default, which
     * accepts only the origin the console is served from.
     */
    Optional<List<String>> allowedOrigins();
}
