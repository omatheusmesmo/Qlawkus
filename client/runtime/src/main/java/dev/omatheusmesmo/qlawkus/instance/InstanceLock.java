package dev.omatheusmesmo.qlawkus.instance;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
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
import java.util.UUID;

/**
 * Makes single-instance an enforced property rather than a convention. Qlawkus serves one owner
 * with one persona, and its scheduled jobs assume they are the only writer: two processes on the
 * same state would race the singleton stores and run every nightly consolidation and curation
 * twice. So the process holds an exclusive OS lock on {@code <state.root>/.instance.lock} for its
 * whole lifetime, and a second instance pointed at the same state fails at boot, naming the holder.
 * It is taken before any other startup observer, so a refused instance never gets to reconcile
 * stores or open a messaging connection first.
 *
 * <p>The lock is advisory and per host, which matches the reference deployment: the state volume
 * is ReadWriteOnce, so every process that can mount it shares the kernel that arbitrates the lock.
 * It does not cover two instances on different hosts sharing only a database.
 *
 * <p>POSIX locks belong to the process, and closing <em>any</em> descriptor of the file drops all
 * of them. A refused instance in the same JVM (dev-mode continuous testing, for one) must therefore
 * be turned away before it opens the file, or its close would silently release the lock it was
 * refused. The claim lives in the system properties because those are the one registry shared by
 * every Quarkus application classloader in the JVM; a static field would exist once per app.
 */
@Startup(Interceptor.Priority.PLATFORM_BEFORE)
@ApplicationScoped
public class InstanceLock {

  static final String LOCK_FILE = ".instance.lock";

  private static final String CLAIM_PREFIX = "qlawkus.instance-lock.";

  private final Path configuredRoot;
  private final String token = UUID.randomUUID().toString();
  private String claim;
  private FileChannel channel;
  private FileLock lock;

  @Inject
  public InstanceLock(AgentConfig config) {
    this(config.state().root());
  }

  public InstanceLock(String root) {
    this.configuredRoot = Path.of(root);
  }

  @PostConstruct
  void acquire() {
    Path root;
    try {
      Files.createDirectories(configuredRoot);
      root = configuredRoot.toRealPath();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not prepare the state root " + configuredRoot, e);
    }
    String key = CLAIM_PREFIX + root;
    if (System.getProperties().putIfAbsent(key, token) != null) {
      throw refused(root, "another instance in this JVM");
    }
    claim = key;
    Path file = root.resolve(LOCK_FILE);
    try {
      channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
          StandardOpenOption.WRITE);
      lock = tryLock(channel);
      if (lock == null) {
        String holder = holder(file);
        abandon();
        throw refused(root, holder);
      }
      channel.truncate(0);
      channel.write(ByteBuffer.wrap(describeSelf().getBytes(StandardCharsets.UTF_8)));
      channel.force(false);
      Log.infof("Holding the instance lock on %s", root);
    } catch (IOException e) {
      abandon();
      throw new UncheckedIOException("Could not take the instance lock on " + file, e);
    }
  }

  @PreDestroy
  void release() {
    if (claim == null) {
      return;
    }
    try {
      if (lock != null) {
        lock.release();
      }
    } catch (IOException e) {
      Log.warnf(e, "Could not release the instance lock on %s", configuredRoot);
    } finally {
      abandon();
    }
  }

  private void abandon() {
    try {
      if (channel != null) {
        channel.close();
      }
    } catch (IOException e) {
      Log.warnf(e, "Could not close the instance lock on %s", configuredRoot);
    } finally {
      lock = null;
      channel = null;
      System.getProperties().remove(claim, token);
      claim = null;
    }
  }

  private static IllegalStateException refused(Path root, String holder) {
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
