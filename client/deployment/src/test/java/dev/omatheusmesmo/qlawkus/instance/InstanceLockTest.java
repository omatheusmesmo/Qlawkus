package dev.omatheusmesmo.qlawkus.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Single-instance guard on the state root. The cross-process case is the one that happens in
 * production (a rolling update or a second {@code docker compose up} on the same volume), so it is
 * exercised with a real second JVM rather than only the same-JVM overlap.
 */
class InstanceLockTest {

  /**
   * A second JVM that either holds the lock until its stdin closes ({@code hold}) or reports
   * whether the lock is currently free ({@code probe}). It writes through the locked channel:
   * opening the file a second time and closing it would drop its own POSIX lock.
   */
  private static final String PROBE_SOURCE = """
      import java.nio.ByteBuffer;
      import java.nio.channels.FileChannel;
      import java.nio.file.*;
      public class LockProbe {
        public static void main(String[] args) throws Exception {
          try (FileChannel channel = FileChannel.open(Path.of(args[1]), StandardOpenOption.CREATE,
              StandardOpenOption.WRITE)) {
            var lock = channel.tryLock();
            if (args[0].equals("probe")) {
              System.out.println(lock == null ? "held" : "free");
              return;
            }
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(("pid=" + ProcessHandle.current().pid()
                + " host=other-pod\\n").getBytes()));
            System.out.println(lock == null ? "held" : "locked");
            System.out.flush();
            System.in.read();
          }
        }
      }
      """;

  @TempDir
  Path tempDir;

  @Test
  void secondInstanceInAnotherProcessRefusesToStart() throws Exception {
    Path root = tempDir.resolve("state");
    Files.createDirectories(root);
    Process holder = probe("hold", root);
    try {
      assertEquals("locked", firstLine(holder), "holder process should take the lock");

      InstanceLock second = new InstanceLock(root.toString());
      IllegalStateException refused = assertThrows(IllegalStateException.class, second::acquire);

      assertTrue(refused.getMessage().contains(root.toString()),
          "the refusal must name the contested state root");
      assertTrue(refused.getMessage().contains("pid=" + holder.pid()),
          "the refusal must name the instance already holding it");
    } finally {
      holder.getOutputStream().close();
      holder.waitFor(10, TimeUnit.SECONDS);
      holder.destroyForcibly();
    }
  }

  @Test
  void refusingASecondInstanceInSameJvmKeepsTheFirstLockHeld() throws Exception {
    InstanceLock first = new InstanceLock(tempDir.toString());
    first.acquire();
    try {
      assertThrows(IllegalStateException.class, new InstanceLock(tempDir.toString())::acquire);

      assertEquals("held", firstLine(probe("probe", tempDir)),
          "refusing the second instance must not release the first one's POSIX lock");
    } finally {
      first.release();
    }
  }

  /**
   * Quarkus loads each application (the dev app and its continuous-testing app, every test app) in
   * its own classloader, so anything static is per app, not per JVM. A refusal decided by a copy of
   * the class the holder never saw must still leave the holder's lock in place.
   */
  @Test
  void refusingAnInstanceLoadedByAnotherClassLoaderKeepsTheFirstLockHeld() throws Exception {
    InstanceLock first = new InstanceLock(tempDir.toString());
    first.acquire();
    try {
      Class<?> isolated = new IsolatingClassLoader().loadClass(InstanceLock.class.getName());
      assertTrue(isolated != InstanceLock.class, "the second app must see its own copy");
      Constructor<?> constructor = isolated.getConstructor(String.class);
      Method acquire = isolated.getDeclaredMethod("acquire");
      acquire.setAccessible(true);
      Object second = constructor.newInstance(tempDir.toString());

      InvocationTargetException refused =
          assertThrows(InvocationTargetException.class, () -> acquire.invoke(second));
      assertTrue(refused.getCause() instanceof IllegalStateException);

      assertEquals("held", firstLine(probe("probe", tempDir)),
          "a refusal from another classloader must not release the first app's POSIX lock");
    } finally {
      first.release();
    }
  }

  @Test
  void releasedLockCanBeTakenByTheNextInstance() {
    InstanceLock first = new InstanceLock(tempDir.toString());
    first.acquire();
    first.release();

    InstanceLock next = new InstanceLock(tempDir.toString());
    assertDoesNotThrow(next::acquire);
    next.release();
  }

  @Test
  void firstBootOnAnEmptyVolumeCreatesTheRoot() throws Exception {
    Path root = tempDir.resolve("fresh/state");
    InstanceLock lock = new InstanceLock(root.toString());
    lock.acquire();
    try {
      String holder = Files.readString(root.resolve(InstanceLock.LOCK_FILE));
      assertTrue(holder.contains("pid=" + ProcessHandle.current().pid()),
          "the lock file should record who holds it, for the next instance's error message");
    } finally {
      lock.release();
    }
  }

  private Process probe(String mode, Path root) throws Exception {
    Path source = tempDir.resolve("LockProbe.java");
    if (!Files.exists(source)) {
      Files.writeString(source, PROBE_SOURCE);
    }
    String java = ProcessHandle.current().info().command().orElse("java");
    return new ProcessBuilder(java, source.toString(),
        mode, root.resolve(InstanceLock.LOCK_FILE).toString()).start();
  }

  /** Defines its own copy of {@link InstanceLock}, delegating every other class to the parent. */
  private static final class IsolatingClassLoader extends ClassLoader {

    IsolatingClassLoader() {
      super(InstanceLockTest.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith(InstanceLock.class.getName())) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        try (InputStream bytes = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
          byte[] code = bytes.readAllBytes();
          return defineClass(name, code, 0, code.length);
        } catch (Exception e) {
          throw new ClassNotFoundException(name, e);
        }
      }
    }
  }

  private static String firstLine(Process process) throws Exception {
    return new BufferedReader(new InputStreamReader(process.getInputStream(),
        StandardCharsets.UTF_8)).readLine();
  }
}
