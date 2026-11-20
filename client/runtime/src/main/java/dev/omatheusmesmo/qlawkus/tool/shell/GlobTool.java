package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;

@QlawTool
@ApplicationScoped
public class GlobTool {
    private final WorkspaceConfinement confinement;
    private final ShellConfig config;

    @Inject
    public GlobTool(WorkspaceConfinement confinement, ShellConfig config) {
        this.confinement = confinement;
        this.config = config;
    }

    @Tool("Find workspace files by glob (*, **, ?, character classes). Returns alphabetically sorted workspace-relative paths. "
            + "Respects nested .gitignore files, never follows symlinks, and reports search limits. No external binary required.")
    public String globFiles(@P("Relative glob, for example **/*.java or src/**/Test?.java") String pattern,
            @P(value = "Directory to search; defaults to the workspace root", required = false) String path,
            @P(value = "Maximum files to return; defaults to the configured maximum (500)", required = false) Integer limit) {
        if (!config.glob().enabled()) {
            return "File glob search is disabled (qlawkus.shell.glob.enabled=false).";
        }
        if (limit != null && limit < 1) {
            return "Error: limit must be positive.";
        }
        try {
            WorkspaceGlob glob = new WorkspaceGlob(pattern, false);
            WorkspaceSearch search = new WorkspaceSearch(confinement, config.search());
            WorkspaceSearch.Scan scan = search.scan(path, glob, false);
            SearchOutput output = new SearchOutput(config.search().maxOutputChars());
            int maximum = Math.clamp(config.glob().maxResults(), 1, 5000);
            int requested = limit == null ? maximum : Math.min(limit, maximum);
            int count = 0;
            for (Path file : scan.files()) {
                if (count == requested) {
                    search.mark("result limit reached");
                    break;
                }
                if (!output.append(search.display(file))) {
                    search.mark("output character budget reached");
                    break;
                }
                count++;
            }
            return output.finish(search);
        } catch (WorkspaceSearch.Stopped e) {
            return "No matches returned. [TRUNCATED: search deadline reached]";
        } catch (IllegalArgumentException e) {
            return "Error: invalid search path or glob. Use a relative glob within the workspace.";
        } catch (IOException e) {
            return "Error: search path does not exist, is unreadable, or contains a symbolic link.";
        }
    }
}
