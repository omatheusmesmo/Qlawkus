package dev.omatheusmesmo.qlawkus.dto;

public record FileResult(
        boolean success,
        String content,
        String error
) {
    public static FileResult ok(String content) {
        return new FileResult(true, content, "");
    }

    public static FileResult fail(String error) {
        return new FileResult(false, "", error);
    }
}
