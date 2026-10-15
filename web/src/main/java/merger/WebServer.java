package merger;

import classes.MergerCLI;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Merge-only web backend (Javalin) reusing {@link MergerCLI#merge} (byte[] in/out).
 *
 * <p>- GET / -> single-page frontend - GET /api/health -> {"ok":true,...} - POST /api/merge ->
 * multipart (game=@file.jar, copies=N), returns merged jar - POST /api/ping -> heartbeat {sid},
 * returns usage stats - GET /api/stats -> {"visits":N,"online":N,"merges":N,"downloads":N}
 */
public class WebServer {
  /** Max upload: ~10MB (game J2ME hiếm khi quá 2MB). */
  static final long MAX_BYTES = 10L * 1024 * 1024;

  /** Web cap: 25 copies (same as desktop). */
  static final int MAX_COPIES = 25;

  static final int DEFAULT_COPIES = 6;

  /** Aggregate usage counters (visits/online/merges), persisted to stats.json. */
  static final StatsStore STATS = StatsStore.loadDefault();

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
              config.routes.post("/api/merge", WebServer::merge);
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

  static void merge(Context ctx) throws Exception {
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

    List<byte[]> jars = new ArrayList<>();
    jars.add(data);
    List<String> names = new ArrayList<>();
    names.add(origName);
    // Silence per-request merge chatter; keep server log to one line.
    PrintStream prev = MergerCLI.LOG;
    MergerCLI.LOG = new PrintStream(new ByteArrayOutputStream());
    byte[] result;
    try {
      result = MergerCLI.merge(jars, names, copies);
    } catch (Exception e) {
      String msg = e.getMessage() == null ? e.toString() : e.getMessage();
      ctx.status(422).json(Map.of("error", "merge failed: " + msg));
      System.out.println("[web] merge failed: " + e);
      return;
    } finally {
      MergerCLI.LOG = prev;
    }
    String outName = MergerCLI.deriveOutputName(origName, copies);
    STATS.countMerge();
    ctx.contentType("application/java-archive");
    ctx.header("Content-Disposition", "attachment; filename=\"" + outName + "\"");
    ctx.result(result);
    System.out.println(
        "[web] merged "
            + origName
            + " x"
            + copies
            + " -> "
            + outName
            + " ("
            + result.length
            + " bytes)");
  }

  static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] tmp = new byte[8192];
    int n;
    while ((n = in.read(tmp)) > 0) b.write(tmp, 0, n);
    return b.toByteArray();
  }
}
