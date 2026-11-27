package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.dto.FileEdit;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Multi-file workspace patch in one call. Paths stay confined to the workspace; each changed file
 * contributes a bounded unified diff in the result.
 */
@QlawTool
@ApplicationScoped
public class ApplyPatchTool {

    @Inject
    FileConfig config;

    @Inject
    WorkspaceConfinement confinement;

    @ToolAccess(AccessClass.DELETE)
    @Tool("Apply a multi-file workspace patch in one step (add, update, delete, or move files). "
            + "Envelope: Begin Patch … End Patch. "
            + "Add File: <path> then file content; "
            + "Update File: <path> with <<<SEARCH … === … >>>; "
            + "Delete File: <path>; "
            + "Move File: <from> then To: <to>. "
            + "Stops on the first failure; returns bounded unified diffs for files changed before the error.")
    public FileResult applyPatch(@P("Full patch text from Begin Patch through End Patch") String patch) {
        if (!config.patch().enabled()) {
            return FileResult.fail("Multi-file patches are disabled (qlawkus.file.patch.enabled=false).");
        }
        try {
            StructuredPatch parsed = StructuredPatchParser.parse(patch);
            StringBuilder summary = new StringBuilder();
            int maxDiff = config.edit().maxDiffChars();
            for (StructuredPatch.PatchOperation operation : parsed.operations()) {
                summary.append(switch (operation) {
                    case StructuredPatch.PatchOperation.Add add -> applyAdd(add, maxDiff);
                    case StructuredPatch.PatchOperation.Update update -> applyUpdate(update, maxDiff);
                    case StructuredPatch.PatchOperation.Delete delete -> applyDelete(delete);
                    case StructuredPatch.PatchOperation.Move move -> applyMove(move);
                }).append('\n');
            }
            return FileResult.ok(summary.toString().strip());
        } catch (StructuredPatchParser.StructuredPatchException e) {
            return FileResult.fail(e.getMessage());
        } catch (ExactFileEdits.Rejected e) {
            return FileResult.fail(e.getMessage());
        }
    }

    private String applyAdd(StructuredPatch.PatchOperation.Add add, int maxDiff) {
        Path target = resolveRequired(add.path());
        if (Files.exists(target)) {
            throw new StructuredPatchParser.StructuredPatchException("Add File refused: " + add.path() + " already exists.");
        }
        String before = "";
        writeText(target, add.content());
        return diffLine(add.path(), before, add.content(), maxDiff);
    }

    private String applyUpdate(StructuredPatch.PatchOperation.Update update, int maxDiff) {
        Path target = resolveRequired(update.path());
        if (!Files.isRegularFile(target)) {
            throw new StructuredPatchParser.StructuredPatchException("Update File refused: " + update.path() + " not found.");
        }
        String original = readText(target);
        ExactFileEdits.Result edited = ExactFileEdits.apply(original,
                List.of(new FileEdit(update.search(), update.replace())), false, (int) config.maxWriteSize());
        writeText(target, edited.content());
        return diffLine(update.path(), original, edited.content(), maxDiff);
    }

    private String applyDelete(StructuredPatch.PatchOperation.Delete delete) {
        Path target = resolveRequired(delete.path());
        if (!Files.isRegularFile(target)) {
            throw new StructuredPatchParser.StructuredPatchException("Delete File refused: " + delete.path() + " not found.");
        }
        String before = readText(target);
        try {
            Files.delete(target);
            Log.infof("PATCH_DELETE | path=%s", delete.path());
        } catch (IOException e) {
            throw new StructuredPatchParser.StructuredPatchException("Delete File failed: " + e.getMessage());
        }
        return "Deleted " + delete.path() + "\n" + diffLine(delete.path(), before, "", config.edit().maxDiffChars());
    }

    private String applyMove(StructuredPatch.PatchOperation.Move move) {
        Path source = resolveRequired(move.from());
        Path destination = resolveRequired(move.to());
        if (!Files.isRegularFile(source)) {
            throw new StructuredPatchParser.StructuredPatchException("Move File refused: " + move.from() + " not found.");
        }
        if (Files.exists(destination)) {
            throw new StructuredPatchParser.StructuredPatchException("Move File refused: destination " + move.to() + " exists.");
        }
        try {
            Files.createDirectories(destination.getParent());
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            Log.infof("PATCH_MOVE | from=%s | to=%s", move.from(), move.to());
        } catch (IOException e) {
            throw new StructuredPatchParser.StructuredPatchException("Move File failed: " + e.getMessage());
        }
        return "Moved " + move.from() + " -> " + move.to();
    }

    private String diffLine(String path, String before, String after, int maxDiff) {
        String diff = FileEditDiff.render(path, before, after, maxDiff);
        return diff.isBlank() ? ("Updated " + path) : diff;
    }

    private Path resolveRequired(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new StructuredPatchParser.StructuredPatchException("Every patch operation needs a workspace-relative path.");
        }
        SecurityResult security = confinement.check(relative);
        if (security.blocked()) {
            throw new StructuredPatchParser.StructuredPatchException(security.reason());
        }
        Path workspace = confinement.getWorkspacePath();
        Path resolved = workspace.resolve(relative).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new StructuredPatchParser.StructuredPatchException("Path escapes the workspace: " + relative);
        }
        return resolved;
    }

    private void writeText(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Log.infof("PATCH_WRITE | path=%s | size=%d", workspaceRelative(target), content.length());
        } catch (IOException e) {
            throw new StructuredPatchParser.StructuredPatchException("Could not write " + target.getFileName() + ": " + e.getMessage());
        }
    }

    private String readText(Path target) {
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StructuredPatchParser.StructuredPatchException("Could not read " + target.getFileName() + ": " + e.getMessage());
        }
    }

    private String workspaceRelative(Path absolute) {
        return confinement.getWorkspacePath().relativize(absolute).toString().replace('\\', '/');
    }
}
