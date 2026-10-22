package dev.omatheusmesmo.qlawkus.instance;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes single-instance an enforced property rather than a convention. Qlawkus serves one owner
 * with one persona, and its scheduled jobs assume they are the only writer: two processes on the
 * same state would race the singleton stores and run every nightly consolidation and curation
 * twice. So the process holds an exclusive OS lock on {@code <state.root>/.instance.lock} for its
 * whole lifetime, and a second instance pointed at the same state fails at boot, naming the holder.
 *
 * <p>The lock is advisory and per host, which matches the reference deployment: the state volume
 * is ReadWriteOnce, so every process that can mount it shares the kernel that arbitrates the lock.
 * It does not cover two instances on different hosts sharing only a database.
 *
 * <p>POSIX locks belong to the process, and closing <em>any</em> descriptor of the file drops all
 * of them. A refused instance in the same JVM (dev-mode continuous testing, for one) must therefore
 * be turned away by {@link #HELD_IN_THIS_JVM} before it opens the file, or its close would silently
 * release the lock it was refused.
 */
@Startup
@ApplicationScoped
public class InstanceLock {

  static final String LOCK_FILE = ".instance.lock";

  private static final Set<Path> HELD_IN_THIS_JVM = ConcurrentHashMap.newKeySet();

  private final Path root;
  private FileChannel channel;
  private FileLock lock;

  @Inject
  public InstanceLock(AgentConfig config) {
    this(config.state().root());
  }

  public InstanceLock(String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @PostConstruct
  void acquire() {
    Path file = root.resolve(LOCK_FILE);
    if (!HELD_IN_THIS_JVM.add(root)) {
      throw refused("another instance in this JVM");
    }
    try {
      Files.createDirectories(root);
      channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
          StandardOpenOption.WRITE);
      lock = tryLock(channel);
      if (lock == null) {
        channel.close();
        HELD_IN_THIS_JVM.remove(root);
        throw refused(holder(file));
      }
      channel.truncate(0);
      channel.write(ByteBuffer.wrap(describeSelf().getBytes(StandardCharsets.UTF_8)));
      channel.force(false);
      Log.infof("Holding the instance lock on %s", root);
    } catch (IOException e) {
      HELD_IN_THIS_JVM.remove(root);
      throw new UncheckedIOException("Could not take the instance lock on " + file, e);
    }
  }

  @PreDestroy
  void release() {
    if (lock == null) {
      return;
    }
    try {
      lock.release();
      channel.close();
    } catch (IOException e) {
      Log.warnf(e, "Could not release the instance lock on %s", root);
    } finally {
      lock = null;
      HELD_IN_THIS_JVM.remove(root);
    }
  }

  private IllegalStateException refused(String holder) {
    return new IllegalStateException("Another Qlawkus instance (" + holder
        + ") already owns the state at " + root + ". Qlawkus runs as a single instance: stop the"
        + " other one, or point this one at a different qlawkus.agent.state.root.");
  }

  private static FileLock tryLock(FileChannel channel) throws IOException {
    try {
      return channel.tryLock();
    } catch (OverlappingFileLockException sameJvm) {
      return null;
    }
  }

  private static String describeSelf() {
    return "pid=" + ProcessHandle.current().pid()
        + " host=" + System.getenv().getOrDefault("HOSTNAME", "unknown") + "\n";
  }

  private static String holder(Path file) {
    try {
      String recorded = Files.readString(file).strip();
      return recorded.isEmpty() ? "holder unknown" : recorded;
    } catch (IOException e) {
      return "holder unknown";
    }
  }
}
