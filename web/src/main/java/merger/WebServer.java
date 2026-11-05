package merger;

import classes.MergerCLI;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Merge-only web backend (Javalin) reusing {@link classes.MergerCLI#merge} (byte[] in/out).
 *
 * <p>- GET / -> single-page frontend - GET /api/health -> {"ok":true,...} - POST /api/merge ->
 * multipart (game=@file.jar, copies=N), returns 202 {"jobId":...} - GET /api/jobs/:id -> progress
 * {"status":queued|running|done|error,"done":N,"total":N,"current":"..."} - GET
 * /api/jobs/:id/download -> merged jar once done - POST /api/ping -> heartbeat {sid}, returns
 * usage stats - GET /api/stats -> {"visits":N,"online":N,"merges":N,"downloads":N}
 */
public class WebServer {
  /** Max upload: ~10MB (game J2ME hiếm khi quá 2MB). */
  static final long MAX_BYTES = 10L * 1024 * 1024;

  /** Web cap: 25 copies (same as desktop). */
  static final int MAX_COPIES = 25;

  static final int DEFAULT_COPIES = 6;

  /** Isolated merge slots (transformer core is not thread-safe); excess load gets 429. */
  static final MergePool MERGE_POOL = new MergePool();

  /** Aggregate usage counters (visits/online/merges), persisted to stats.json. */
  static final StatsStore STATS = StatsStore.loadDefault();

  /** Async merge jobs (one worker per pool slot, bounded FIFO queue). */
  static final JobManager JOBS = new JobManager(MERGE_POOL, STATS);

  public static Javalin start(int port) {
    Javalin app =
        Javalin.create(
            config -> {
              config.jetty.multipartConfig.maxFileSize(MAX_BYTES, SizeUnit.BYTES);
              config.jetty.multipartConfig.maxInMemoryFileSize(10, SizeUnit.MB);
              config.jetty.multipartConfig.maxTotalRequestSize(
                  MAX_BYTES + 1024 * 1024, SizeUnit.BYTES);
              config.staticFiles.add("/web", Location.CLASSPATH);
              config.spaRoot.addFile("/", "/web/index.html");
              config.routes.get("/api/health", WebServer::health);
              config.routes.post("/api/merge", WebServer::submitJob);
              config.routes.get("/api/jobs/{id}", WebServer::jobStatus);
              config.routes.get("/api/jobs/{id}/download", WebServer::jobDownload);
              config.routes.post("/api/ping", WebServer::ping);
              config.routes.get("/api/stats", WebServer::stats);
            });
    app.start(port);
    System.out.println("[web] listening on http://localhost:" + port);
    return app;
  }

  static void health(Context ctx) {
    ctx.json(Map.of("ok", true, "app", "j2me-merger", "maxUploadMB", 10, "maxCopies", MAX_COPIES));
  }

  static void ping(Context ctx) {
    String sid = "";
    try {
      Map<?, ?> body = ctx.bodyAsClass(Map.class);
      Object v = body == null ? null : body.get("sid");
      if (v != null) sid = String.valueOf(v).trim();
    } catch (Exception e) {
      ctx.status(400).json(Map.of("error", "bad ping body, want {\"sid\":\"...\"}"));
      return;
    }
    if (sid.length() < 8 || sid.length() > 64 || !sid.matches("[A-Za-z0-9-]+")) {
      ctx.status(400).json(Map.of("error", "bad sid"));
      return;
    }
    STATS.ping(sid);
    ctx.json(STATS.snapshot());
  }

  static void stats(Context ctx) {
    ctx.json(STATS.snapshot());
  }

  /** Validates the upload, queues a merge job, returns 202 {"jobId":...} (or 429 when full). */
  static void submitJob(Context ctx) throws Exception {
    UploadedFile game = ctx.uploadedFile("game");
    if (game == null || game.size() == 0) {
      ctx.status(400).json(Map.of("error", "missing file field 'game'"));
      return;
    }
    if (game.size() > MAX_BYTES) {
      ctx.status(413).json(Map.of("error", "file too large (max ~10MB)"));
      return;
    }
    int copies = DEFAULT_COPIES;
    String copiesStr = ctx.formParam("copies");
    if (copiesStr != null && !copiesStr.trim().isEmpty()) {
      try {
        copies = Integer.parseInt(copiesStr.trim());
      } catch (NumberFormatException e) {
        ctx.status(400).json(Map.of("error", "copies must be a number"));
        return;
      }
    }
    if (copies < 1 || copies > MAX_COPIES) {
      ctx.status(400).json(Map.of("error", "copies must be 1.." + MAX_COPIES));
      return;
    }
    String origName = game.filename();
    if (origName == null || origName.isEmpty()) origName = "game.jar";
    origName = origName.replace('\\', '/');
    int s = origName.lastIndexOf('/');
    if (s >= 0) origName = origName.substring(s + 1);
    if (!origName.toLowerCase().endsWith(".jar")) origName += ".jar";

    byte[] data = readAll(game.content());
    if (data.length > MAX_BYTES) {
      ctx.status(413).json(Map.of("error", "file too large (max ~10MB)"));
      return;
    }

    JobManager.Job job;
    try {
      job = JOBS.submit(data, origName, copies);
    } catch (RejectedExecutionException e) {
      ctx.status(429);
      ctx.header("Retry-After", "5");
      ctx.json(Map.of("error", "server busy, please retry in a few seconds"));
      return;
    }
    ctx.status(202).json(Map.of("jobId", job.id, "queued", JOBS.queuedAhead(job)));
  }

  static void jobStatus(Context ctx) {
    JobManager.Job job = JOBS.get(ctx.pathParam("id"));
    if (job == null) {
      ctx.status(404).json(Map.of("error", "unknown or expired job"));
      return;
    }
    ctx.json(job.snapshot(JOBS.queuedAhead(job)));
  }

  static void jobDownload(Context ctx) {
    JobManager.Job job = JOBS.get(ctx.pathParam("id"));
    if (job == null) {
      ctx.status(404).json(Map.of("error", "unknown or expired job"));
      return;
    }
    if (job.status == JobManager.Status.QUEUED || job.status == JobManager.Status.RUNNING) {
      ctx.status(409).json(Map.of("error", "job not ready, try again"));
      return;
    }
    if (job.status == JobManager.Status.ERROR || job.result == null) {
      String msg = job.error.isEmpty() ? "merge failed" : job.error;
      ctx.status(422).json(Map.of("error", "merge failed: " + msg));
      return;
    }
    ctx.contentType("application/java-archive");
    ctx.header("Content-Disposition", "attachment; filename=\"" + job.outName + "\"");
    ctx.result(job.result);
  }

  static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] tmp = new byte[8192];
    int n;
    while ((n = in.read(tmp)) > 0) b.write(tmp, 0, n);
    return b.toByteArray();
  }
}
