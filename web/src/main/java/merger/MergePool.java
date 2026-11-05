package merger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Isolated merge slots. The bytecode transformer (package classes.*) keeps shared
 * static workspaces that are NOT thread-safe (parallel in-JVM merges corrupt each
 * other), so every slot owns a child-first ClassLoader with its own static state
 * and serves at most one merge at a time. Core stays untouched.
 */
public class MergePool {
  static final int SLOTS = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));

  private final BlockingQueue<URLClassLoader> free;

  public MergePool() {
    URL code = WebServer.class.getProtectionDomain().getCodeSource().getLocation();
    free = new ArrayBlockingQueue<>(SLOTS);
    for (int i = 0; i < SLOTS; i++) free.add(slotLoader(new URL[] {code}));
    System.out.println("[web] merge slots=" + SLOTS);
  }

  /** Borrow a slot, or null when all busy (caller should answer 429). */
  public URLClassLoader borrow() {
    return free.poll();
  }

  public void release(URLClassLoader slot) {
    if (slot != null) free.offer(slot);
  }

  @SuppressWarnings("unchecked")
  public static byte[] merge(URLClassLoader slot, List<byte[]> jars, List<String> names, int copies)
      throws Exception {
    Class<?> cli = Class.forName("classes.MergerCLI", true, slot);
    // Silence per-request chatter; statics are slot-private and the slot is
    // held exclusively, so save/restore cannot race.
    Field logField = cli.getField("LOG");
    PrintStream prev = (PrintStream) logField.get(null);
    logField.set(null, new PrintStream(new ByteArrayOutputStream()));
    try {
      Method m = cli.getMethod("merge", List.class, List.class, int.class);
      return (byte[]) m.invoke(null, jars, names, copies);
    } catch (InvocationTargetException e) {
      Throwable c = e.getCause();
      if (c instanceof Exception) throw (Exception) c;
      throw new RuntimeException(c);
    } finally {
      logField.set(null, prev);
    }
  }

  private static URLClassLoader slotLoader(URL[] urls) {
    return new URLClassLoader(urls, MergePool.class.getClassLoader()) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
          if (name.startsWith("classes.")) {
            Class<?> c = findLoadedClass(name);
            if (c == null) c = findClass(name);
            if (resolve) resolveClass(c);
            return c;
          }
          return super.loadClass(name, resolve);
        }
      }
    };
  }
}
