package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.file")
public interface FileConfig {

    /**
     * Maximum file size in bytes allowed for read operations.
     */
    @WithDefault("5242880")
    long maxReadSize();

    /**
     * Maximum file size in bytes allowed for write operations.
     */
    @WithDefault("10485760")
    long maxWriteSize();

    /**
     * Character encoding for file read/write operations.
     */
    @WithDefault("UTF-8")
    String encoding();

    /**
     * Limits for numbered, paginated reads. Omit offset and limit to retain the full-file format.
     */
    Read read();

    /**
     * Exact, atomic text edits within workspace files.
     */
    Edit edit();

    /**
     * Multi-file patch envelope (add, update, delete, move) in a single tool call.
     */
    Patch patch();

    interface Patch {
        /**
         * Enable the applyPatch tool. When false, calls return an error without touching files.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface Edit {
        /**
         * Enable exact text editing. Unmatched or ambiguous text never changes a file.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Maximum characters returned by an edit, including its diff preview. Clamped to 512–64000.
         */
        @WithDefault("12000")
        int maxDiffChars();
    }

    interface Read {
        /**
         * Maximum lines returned by a paginated read. Larger requested pages are capped here.
         */
        @WithDefault("2000")
        int maxLinesPerPage();

        /**
         * Maximum characters retained from one line in a paginated read. Longer lines are marked
         * as truncated without loading the whole line into memory.
         */
        @WithDefault("2000")
        int maxLineLength();
    }
}
