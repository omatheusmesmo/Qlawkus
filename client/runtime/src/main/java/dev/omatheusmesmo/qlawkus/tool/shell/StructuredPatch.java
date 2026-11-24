package dev.omatheusmesmo.qlawkus.tool.shell;

import java.util.List;

/** Parsed multi-file patch envelope (Begin Patch … End Patch). */
record StructuredPatch(List<PatchOperation> operations) {

    sealed interface PatchOperation permits PatchOperation.Add, PatchOperation.Update, PatchOperation.Delete,
            PatchOperation.Move {

        record Add(String path, String content) implements PatchOperation {
        }

        record Update(String path, String search, String replace) implements PatchOperation {
        }

        record Delete(String path) implements PatchOperation {
        }

        record Move(String from, String to) implements PatchOperation {
        }
    }
}
