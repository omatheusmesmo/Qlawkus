package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.BufferedReader;
import java.io.IOException;

record PtyConnection(Process process, BufferedReader output, Resize resize, Runnable stop) {
    @FunctionalInterface
    interface Resize {
        void apply(int columns, int rows) throws IOException;
    }
}
