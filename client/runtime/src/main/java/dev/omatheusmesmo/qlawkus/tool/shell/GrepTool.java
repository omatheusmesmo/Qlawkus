package dev.omatheusmesmo.qlawkus.tool.shell;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Iterator;

@QlawTool
@ApplicationScoped
public class GrepTool {
    private static final int MAX_SEARCH_LINE_CHARS = 65536;
    private final WorkspaceConfinement confinement;
    private final ShellConfig config;
    private final FileConfig fileConfig;

    @Inject
    public GrepTool(WorkspaceConfinement confinement, ShellConfig config, FileConfig fileConfig) {
        this.confinement = confinement;
        this.config = config;
        this.fileConfig = fileConfig;
    }

    @Tool("Search workspace text with a linear-time regex, or literal text. Returns path:line:text; context uses path-line-text. "
            + "Respects nested .gitignore, skips symlinks/binary/oversized files and reports limits. "
            + "Regex supports alternation, groups and character classes, but not lookaround or backreferences. No external binary required.")
    public String grepFiles(@P("Regex or literal search text, at most 1024 characters") String pattern,
            @P(value = "File or directory; defaults to workspace root", required = false) String path,
            @P(value = "Optional relative file glob; a basename glob such as *.java matches at any depth", required = false) String include,
            @P(value = "Ignore letter case; default false", required = false) Boolean ignoreCase,
            @P(value = "Treat pattern as literal text; default false", required = false) Boolean literal,
            @P(value = "Lines before and after a match; default zero, capped by configuration (5)", required = false) Integer context,
            @P(value = "Maximum matching lines; defaults to configured maximum (100)", required = false) Integer limit) {
        if (!config.grep().enabled()) {
            return "Content search is disabled (qlawkus.shell.grep.enabled=false).";
        }
        if (pattern == null || pattern.isEmpty() || pattern.length() > 1024) {
            return "Error: provide a search pattern of 1 to 1024 characters.";
        }
        if (limit != null && limit < 1 || context != null && context < 0) {
            return "Error: limit must be positive and context must not be negative.";
        }
        try {
            Pattern regex = Pattern.compile(Boolean.TRUE.equals(literal) ? Pattern.quote(pattern) : pattern,
                    Boolean.TRUE.equals(ignoreCase) ? Pattern.CASE_INSENSITIVE : 0);
            WorkspaceGlob glob = include == null || include.isBlank() ? null : new WorkspaceGlob(include, true);
            WorkspaceSearch search = new WorkspaceSearch(confinement, config.search());
            WorkspaceSearch.Scan scan = search.scan(path, glob, true);
            SearchOutput output = new SearchOutput(config.search().maxOutputChars());
            int maximum = Math.clamp(config.grep().maxResults(), 1, 1000);
            int requested = limit == null ? maximum : Math.min(limit, maximum);
            int around = context == null ? 0 : Math.min(context, Math.clamp(config.search().maxContextLines(), 0, 20));
            int lineLimit = Math.clamp(config.search().maxLineLength(), 1, 10000);
            Charset charset = Charset.forName(fileConfig.encoding());
            int matches = 0;
            try {
                for (Path file : scan.files()) {
                    byte[] bytes;
                    try {
                        bytes = search.read(file, search.maxFileBytes);
                    } catch (IOException e) {
                        search.skipped++;
                        continue;
                    }
                    if (bytes == null) {
                        continue;
                    }
                    String content;
                    try {
                        content = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                    } catch (CharacterCodingException e) {
                        search.skipped++;
                        continue;
                    }
                    if (content.indexOf('\0') >= 0) {
                        search.skipped++;
                        continue;
                    }
                    if (content.startsWith("\ufeff")) {
                        content = content.substring(1);
                    }
                    String label = search.display(file);
                    ArrayDeque<Line> before = new ArrayDeque<>();
                    Iterator<String> lines = content.lines().iterator();
                    int number = 0;
                    int emitted = 0;
                    int after = 0;
                    boolean atLimit = false;
                    while (lines.hasNext()) {
                        search.checkTime();
                        String text = lines.next();
                        number++;
                        if (text.length() > MAX_SEARCH_LINE_CHARS) {
                            search.longLines++;
                            before.clear();
                            after = 0;
                            if (atLimit) {
                                return output.finish(search);
                            }
                            continue;
                        }
                        boolean match = !atLimit && regex.matcher(text).find();
                        if (match) {
                            for (Line line : before) {
                                if (line.number > emitted) {
                                    if (!emit(output, label, line.number, line.text, false, lineLimit)) {
                                        search.mark("output character budget reached");
                                        return output.finish(search);
                                    }
                                    emitted = line.number;
                                }
                            }
                            if (!emit(output, label, number, text, true, lineLimit)) {
                                search.mark("output character budget reached");
                                return output.finish(search);
                            }
                            emitted = number;
                            matches++;
                            after = around;
                            if (matches == requested) {
                                search.mark("result limit reached");
                                atLimit = true;
                                if (after == 0) {
                                    return output.finish(search);
                                }
                            }
                        } else if (after > 0) {
                            if (!emit(output, label, number, text, false, lineLimit)) {
                                search.mark("output character budget reached");
                                return output.finish(search);
                            }
                            emitted = number;
                            if (--after == 0 && atLimit) {
                                return output.finish(search);
                            }
                        }
                        if (around > 0) {
                            before.addLast(new Line(number, text));
                            if (before.size() > around) {
                                before.removeFirst();
                            }
                        }
                    }
                    if (atLimit) {
                        return output.finish(search);
                    }
                }
            } catch (WorkspaceSearch.Stopped ignored) {
                // Retain the results preceding the explicit budget/deadline.
            }
            return output.finish(search);
        } catch (WorkspaceSearch.Stopped e) {
            return "No matches returned. [TRUNCATED: search deadline reached]";
        } catch (PatternSyntaxException e) {
            return "Error: invalid or unsupported regex. Use RE2 syntax without lookaround or backreferences.";
        } catch (IllegalArgumentException e) {
            return "Error: invalid search path, file glob or configured text encoding.";
        } catch (IOException e) {
            return "Error: search path does not exist, is unreadable, or contains a symbolic link.";
        }
    }

    private static boolean emit(SearchOutput output, String path, int number, String text, boolean match, int lineLimit) {
        if (text.length() > lineLimit) {
            int end = lineLimit;
            if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            text = text.substring(0, end) + " [line truncated]";
        }
        char separator = match ? ':' : '-';
        return output.append(path + separator + number + separator + text);
    }

    private record Line(int number, String text) {}
}
