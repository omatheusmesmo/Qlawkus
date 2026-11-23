package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Launches a Linux child with a controlling terminal supplied by util-linux. */
final class LinuxScriptPty {
    private static final String SCRIPT = "/usr/bin/script";
    private static final String STTY = "/usr/bin/stty";
    private static final Pattern SLAVE_DEVICE = Pattern.compile("/dev/pts/[0-9]+");
    private static final int START_TIMEOUT_SECONDS = 5;

    private LinuxScriptPty() {
    }

    static PtyConnection start(String command, File directory, Map<String, String> environment,
            int columns, int rows) throws IOException {
        if (!"Linux".equals(System.getProperty("os.name")) || !Files.isExecutable(Path.of(SCRIPT))) {
            throw new IOException("Native PTY sessions require Linux with util-linux (script).");
        }
        // script uses SHELL to interpret --command. Keep the bootstrap on sh and restore the
        // caller's environment before executing their command, including an intentionally unset SHELL.
        String shell = environment.get("SHELL");
        String restoreShell = shell == null ? "unset SHELL" : "export SHELL=" + quote(shell);
        String bootstrap = STTY + " rows " + rows + " cols " + columns + " && /usr/bin/tty && "
                + restoreShell + " && exec /bin/sh -c " + quote(command);
        var builder = new ProcessBuilder(SCRIPT, "--quiet", "--return", "--flush", "--command", bootstrap, "/dev/null")
                .directory(directory).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);
        builder.environment().put("SHELL", "/bin/sh");

        Process process = builder.start();
        var output = process.inputReader(StandardCharsets.UTF_8);
        var handshake = new FutureTask<>(output::readLine);
        Thread.startVirtualThread(handshake);
        boolean started = false;
        try {
            String device = handshake.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (device == null || !SLAVE_DEVICE.matcher(device).matches()) {
                throw new IOException("Native PTY did not allocate a Linux terminal.");
            }
            started = true;
            return new PtyConnection(process, output, (width, height) -> resize(process, device, width, height),
                    () -> stop(process));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while starting the terminal.", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Could not read the native terminal during startup.", failure);
        } finally {
            if (!started) {
                stop(process);
                handshake.cancel(true);
            }
        }
    }

    private static void resize(Process terminal, String device, int cols, int rows) throws IOException {
        if (!terminal.isAlive()) {
            throw new IOException("The terminal has exited.");
        }
        // While script holds the PTY master open, this path refers to its slave and cannot be reassigned.
        var builder = new ProcessBuilder(STTY, "-F", device, "rows", Integer.toString(rows),
                "cols", Integer.toString(cols))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        Process operation = builder.start();
        try {
            if (!operation.waitFor(START_TIMEOUT_SECONDS, TimeUnit.SECONDS) || operation.exitValue() != 0) {
                throw new IOException("Could not resize the native terminal.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resizing the terminal.", failure);
        } finally {
            operation.destroyForcibly();
        }
    }

    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
