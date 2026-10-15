package merger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregate usage counters persisted to stats.json next to the working directory.
 *
 * <p>Only random session ids (last-seen timestamps) are kept in memory; no IPs, no user
 * files. Totals (visits/merges/downloads) survive restarts via the JSON file.
 */
public class StatsStore {
  /** A session counts as online if seen within this window. */
  static final long ONLINE_WINDOW_MS = 60_000;

  /** Sessions unseen longer than this are dropped to bound memory. */
  static final long STALE_AFTER_MS = 5 * 60_000;

  private final Path file;
  private final ObjectMapper json = new ObjectMapper();
  private final AtomicLong visits = new AtomicLong();
  private final AtomicLong merges = new AtomicLong();
  private final AtomicLong downloads = new AtomicLong();
  private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

  public StatsStore(Path file) {
    this.file = file;
    load();
  }

  public static StatsStore loadDefault() {
    return new StatsStore(Paths.get("stats.json"));
  }

  /**
   * Records a heartbeat. Returns true when the session id is new (counts as one visit).
   */
  public boolean ping(String sid) {
    long now = System.currentTimeMillis();
    boolean isNew = sessions.put(sid, now) == null;
    sessions.entrySet().removeIf(e -> now - e.getValue() > STALE_AFTER_MS);
    if (isNew) {
      visits.incrementAndGet();
      save();
    }
    return isNew;
  }

  /** Records one successful merge (the web UI auto-downloads it, so downloads too). */
  public void countMerge() {
    merges.incrementAndGet();
    downloads.incrementAndGet();
    save();
  }

  public Map<String, Object> snapshot() {
    long now = System.currentTimeMillis();
    long online = sessions.values().stream().filter(t -> now - t <= ONLINE_WINDOW_MS).count();
    return Map.of(
        "visits", visits.get(),
        "online", online,
        "merges", merges.get(),
        "downloads", downloads.get());
  }

  private synchronized void save() {
    try {
      Map<String, Object> data =
          Map.of("visits", visits.get(), "merges", merges.get(), "downloads", downloads.get());
      Files.writeString(file, json.writeValueAsString(data));
    } catch (Exception e) {
      System.out.println("[web] stats save failed: " + e);
    }
  }

  @SuppressWarnings("unchecked")
  private void load() {
    try {
      if (!Files.exists(file)) return;
      Map<String, Object> data = json.readValue(Files.readString(file), Map.class);
      visits.set(number(data, "visits"));
      merges.set(number(data, "merges"));
      downloads.set(number(data, "downloads"));
    } catch (Exception e) {
      System.out.println("[web] stats load failed, starting from zero: " + e);
    }
  }

  private static long number(Map<String, Object> data, String key) {
    Object v = data.get(key);
    return v instanceof Number ? ((Number) v).longValue() : 0L;
  }
}
