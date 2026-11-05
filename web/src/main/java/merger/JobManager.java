package merger;

import classes.MergerCLI;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async merge jobs with per-class progress. One worker thread per {@link MergePool} slot
 * (transformer statics are slot-private); extra submissions wait in a bounded FIFO queue,
 * beyond that callers get 429. Results are kept in RAM with a TTL, then purged.
 */
public class JobManager {
  public enum Status {
    QUEUED,
    RUNNING,
    DONE,
    ERROR
  }

  public static final class Job {
    public final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    public final byte[] input;
    public final String inName;
    public final int copies;
    public final long createdAt = System.currentTimeMillis();
    public volatile Status status = Status.QUEUED;
    public final AtomicInteger done = new AtomicInteger();
    public volatile int total = 0;
    public volatile String current = "";
    public volatile String error = "";
    public volatile byte[] result = null;
    public volatile String outName = "";
    public volatile long finishedAt = 0;

    Job(byte[] input, String inName, int copies) {
      this.input = input;
      this.inName = inName;
      this.copies = copies;
    }

    public Map<String, Object> snapshot(int queuedAhead) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("status", status.name().toLowerCase());
      m.put("done", done.get());
      m.put("total", total);
      m.put("current", current);
      m.put("queued", queuedAhead);
      if (!error.isEmpty()) m.put("error", error);
      if (!outName.isEmpty()) m.put("file", outName);
      return m;
    }
  }

  /** Max jobs waiting for a free slot; beyond this submissions are rejected (429). */
  static final int MAX_QUEUE = 10;

  /** Finished/failed results are downloadable for this long, then purged from RAM. */
  static final long RESULT_TTL_MS = 10 * 60 * 1000;

  private final MergePool pool;
  private final StatsStore stats;
  private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
  private final ThreadPoolExecutor exec;

  public JobManager(MergePool pool, StatsStore stats) {
    this.pool = pool;
    this.stats = stats;
    this.exec =
        new ThreadPoolExecutor(
            MergePool.SLOTS,
            MergePool.SLOTS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUE),
            r -> {
              Thread t = new Thread(r, "merge-worker");
              t.setDaemon(true);
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());
    ScheduledExecutorService janitor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "job-janitor");
      t.setDaemon(true);
      return t;
    });
    janitor.scheduleAtFixedRate(this::purge, 1, 1, TimeUnit.MINUTES);
  }

  /** Queues a merge; throws RejectedExecutionException when all slots + queue are full. */
  public Job submit(byte[] data, String origName, int copies) {
    Job job = new Job(data, origName, copies);
    jobs.put(job.id, job);
    try {
      exec.execute(() -> run(job));
    } catch (RejectedExecutionException e) {
      jobs.remove(job.id);
      throw e;
    }
    return job;
  }

  public Job get(String id) {
    return id == null ? null : jobs.get(id);
  }

  public int queuedAhead(Job job) {
    if (job.status != Status.QUEUED) return 0;
    return exec.getQueue().size();
  }

  private void run(Job job) {
    long t0 = System.currentTimeMillis();
    job.status = Status.RUNNING;
    URLClassLoader slot = pool.borrow();
    if (slot == null) {
      fail(job, "no free merge slot");
      return;
    }
    try {
      List<byte[]> jars = new ArrayList<>();
      jars.add(job.input);
      List<String> names = new ArrayList<>();
      names.add(job.inName);
      byte[] result =
          MergePool.merge(
              slot,
              jars,
              names,
              job.copies,
              (proxy, method, args) -> {
                job.done.set((Integer) args[0]);
                job.total = (Integer) args[1];
                job.current = String.valueOf(args[2]);
                return null;
              });
      job.result = result;
      job.outName = MergerCLI.deriveOutputName(job.inName, job.copies);
      job.status = Status.DONE;
      stats.countMerge();
      System.out.println(
          "[web] merged "
              + job.inName
              + " x"
              + job.copies
              + " -> "
              + job.outName
              + " ("
              + result.length
              + " bytes, "
              + (System.currentTimeMillis() - t0)
              + " ms)");
    } catch (Exception e) {
      fail(job, e.getMessage() == null ? e.toString() : e.getMessage());
      System.out.println("[web] merge failed: " + e);
    } finally {
      pool.release(slot);
      job.finishedAt = System.currentTimeMillis();
    }
  }

  private void fail(Job job, String msg) {
    job.error = msg;
    job.status = Status.ERROR;
    job.finishedAt = System.currentTimeMillis();
  }

  private void purge() {
    long now = System.currentTimeMillis();
    jobs.entrySet()
        .removeIf(
            e -> {
              Job j = e.getValue();
              return (j.status == Status.DONE || j.status == Status.ERROR)
                  && j.finishedAt > 0
                  && now - j.finishedAt > RESULT_TTL_MS;
            });
  }
}
