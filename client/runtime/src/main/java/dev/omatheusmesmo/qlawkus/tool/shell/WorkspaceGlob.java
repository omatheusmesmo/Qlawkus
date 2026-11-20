package dev.omatheusmesmo.qlawkus.tool.shell;

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Segment matching keeps glob evaluation linear in each path segment, including hostile wildcard patterns. */
final class WorkspaceGlob {
    private final List<Pattern> segments;

    WorkspaceGlob(String glob, boolean matchBasename) {
        if (glob == null || glob.isBlank() || glob.length() > 1024 || glob.startsWith("/")) {
            throw new IllegalArgumentException("Provide a relative glob of 1 to 1024 characters");
        }
        if (matchBasename && !glob.contains("/")) {
            glob = "**/" + glob;
        }
        segments = new ArrayList<>();
        for (String segment : glob.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Glob segments must not be empty, '.' or '..'");
            }
            segments.add(segment.equals("**") ? null : Pattern.compile(segmentRegex(segment), Pattern.DOTALL));
        }
    }

    boolean matches(String path) {
        String[] names = path.split("/");
        boolean[] positions = new boolean[names.length + 1];
        positions[0] = true;
        for (Pattern segment : segments) {
            boolean[] next = new boolean[positions.length];
            if (segment == null) {
                boolean reachable = false;
                for (int i = 0; i < positions.length; i++) {
                    reachable |= positions[i];
                    next[i] = reachable;
                }
            } else {
                for (int i = 0; i < names.length; i++) {
                    next[i + 1] = positions[i] && segment.matcher(names[i]).matches();
                }
            }
            positions = next;
        }
        return positions[names.length];
    }

    private static String segmentRegex(String segment) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else if (c == '\\') {
                if (++i == segment.length()) {
                    throw new IllegalArgumentException("A glob must not end with an escape");
                }
                int start = i;
                if (Character.isHighSurrogate(segment.charAt(i)) && i + 1 < segment.length()
                        && Character.isLowSurrogate(segment.charAt(i + 1))) {
                    i++;
                }
                regex.append(Pattern.quote(segment.substring(start, i + 1)));
            } else if (c == '[') {
                int end = segment.indexOf(']', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed glob character class");
                }
                String characterClass = segment.substring(i + 1, end);
                if (characterClass.startsWith("!")) {
                    characterClass = '^' + characterClass.substring(1);
                }
                regex.append('[').append(characterClass).append(']');
                i = end;
            } else {
                int start = i;
                if (Character.isHighSurrogate(c) && i + 1 < segment.length() && Character.isLowSurrogate(segment.charAt(i + 1))) {
                    i++;
                }
                regex.append(Pattern.quote(segment.substring(start, i + 1)));
            }
        }
        return regex.toString();
    }
}
