package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opens absolute, lexically validated paths without following symlink components. */
public final class ConfinedFileRead {

    private ConfinedFileRead() {
    }

    /** Anchored handles prevent a parent directory swapped for a symlink from redirecting a content read. */
    public static InputStream open(Path path, Runnable beforeDirectory) throws IOException {
        List<DirectoryStream<Path>> directories = new ArrayList<>();
        SeekableByteChannel channel = null;
        try {
            Path filesystemRoot = path.getRoot();
            DirectoryStream<Path> first = Files.newDirectoryStream(filesystemRoot);
            directories.add(first);
            if (!(first instanceof SecureDirectoryStream<Path> current)) {
                throw new IOException("Filesystem does not support secure directory access");
            }
            Path relative = filesystemRoot.relativize(path);
            for (int i = 0; i < relative.getNameCount() - 1; i++) {
                beforeDirectory.run();
                current = current.newDirectoryStream(relative.getName(i), LinkOption.NOFOLLOW_LINKS);
                directories.add(current);
            }
            channel = current.newByteChannel(relative.getFileName(), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            return Channels.newInputStream(channel);
        } finally {
            IOException failure = null;
            for (int i = directories.size() - 1; i >= 0; i--) {
                try {
                    directories.get(i).close();
                } catch (IOException e) {
                    failure = e;
                }
            }
            if (failure != null) {
                if (channel != null) {
                    channel.close();
                }
                throw failure;
            }
        }
    }
}
