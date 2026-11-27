package dev.omatheusmesmo.qlawkus.tool.access;

import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@link AccessClass} of a tool from the verb that leads its method name, so a tool needs no
 * annotation when it is named for what it does. Only the whole first word counts: {@code listEvents}
 * is a read, {@code listing} and {@code fileList} are not.
 */
public final class AccessClassInference {

    private static final Map<String, AccessClass> VERBS = Map.ofEntries(
            Map.entry("list", AccessClass.READ),
            Map.entry("get", AccessClass.READ),
            Map.entry("read", AccessClass.READ),
            Map.entry("search", AccessClass.READ),
            Map.entry("find", AccessClass.READ),
            Map.entry("fetch", AccessClass.READ),
            Map.entry("check", AccessClass.READ),
            Map.entry("query", AccessClass.READ),
            Map.entry("download", AccessClass.READ),
            Map.entry("glob", AccessClass.READ),
            Map.entry("grep", AccessClass.READ),
            Map.entry("generate", AccessClass.READ),
            Map.entry("analyze", AccessClass.READ),
            Map.entry("suggest", AccessClass.READ),
            Map.entry("test", AccessClass.READ),
            Map.entry("create", AccessClass.CREATE),
            Map.entry("add", AccessClass.CREATE),
            Map.entry("schedule", AccessClass.CREATE),
            Map.entry("save", AccessClass.CREATE),
            Map.entry("install", AccessClass.CREATE),
            Map.entry("update", AccessClass.UPDATE),
            Map.entry("set", AccessClass.UPDATE),
            Map.entry("edit", AccessClass.UPDATE),
            Map.entry("rename", AccessClass.UPDATE),
            Map.entry("write", AccessClass.UPDATE),
            Map.entry("untrash", AccessClass.UPDATE),
            Map.entry("delete", AccessClass.DELETE),
            Map.entry("remove", AccessClass.DELETE),
            Map.entry("cancel", AccessClass.DELETE),
            Map.entry("trash", AccessClass.DELETE),
            Map.entry("send", AccessClass.SEND),
            Map.entry("share", AccessClass.SEND),
            Map.entry("invite", AccessClass.SEND),
            Map.entry("post", AccessClass.SEND),
            Map.entry("reply", AccessClass.SEND),
            Map.entry("run", AccessClass.EXECUTE),
            Map.entry("exec", AccessClass.EXECUTE),
            Map.entry("shell", AccessClass.EXECUTE));

    private AccessClassInference() {
    }

    /** The class for the leading verb of {@code methodName}, or empty when it has none this knows. */
    public static Optional<AccessClass> fromMethodName(String methodName) {
        return Optional.ofNullable(VERBS.get(leadingWord(methodName)));
    }

    private static String leadingWord(String methodName) {
        int end = 0;
        while (end < methodName.length() && Character.isLowerCase(methodName.charAt(end))) {
            end++;
        }
        return methodName.substring(0, end);
    }
}
