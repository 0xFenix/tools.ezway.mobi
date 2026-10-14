package merger;

import classes.MergerCLI;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Merge-only web backend reusing {@link MergerCLI#merge} (byte[] in/out).
 * Zero new dependencies: JDK built-in HttpServer, Java 8 compatible.
 *
 * Run: java -jar merger.jar --serve [--port 8080]
 *  - GET  /            -> single-page frontend
 *  - GET  /api/health  -> {"ok":true,...}
 *  - POST /api/merge   -> multipart (game=@file.jar, copies=N), returns merged jar
 */
public class WebServer {
    /** Max upload body: 32MB (game jars are usually < 2MB). */
    static final int MAX_BODY = 32 * 1024 * 1024;
    /** Web cap: 10 copies (desktop allows 25). */
    static final int MAX_COPIES = 10;

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/merge", new MergeHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[web] listening on http://0.0.0.0:" + port);
    }

    // ---- static frontend ----

    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/web/index.html";
            else if (path.startsWith("/")) path = "/web" + path;
            // Only serve the single page; anything else -> index (merge-only tool).
            if (!path.equals("/web/index.html")) {
                sendText(ex, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }
            InputStream in = WebServer.class.getResourceAsStream(path);
            if (in == null) {
                sendText(ex, 500, "Missing embedded frontend: " + path, "text/plain; charset=utf-8");
                return;
            }
            byte[] data = readAll(in);
            in.close();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, data.length);
            OutputStream out = ex.getResponseBody();
            out.write(data);
            out.close();
        }
    }

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            sendText(ex, 200, "{\"ok\":true,\"app\":\"j2me-merger\",\"maxUploadMB\":30,\"maxCopies\":" + MAX_COPIES + "}",
                    "application/json; charset=utf-8");
        }
    }

    // ---- merge endpoint ----

    static class MergeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                sendText(ex, 405, "{\"error\":\"POST only\"}", "application/json; charset=utf-8");
                return;
            }
            String ctype = ex.getRequestHeaders().getFirst("Content-Type");
            if (ctype == null || !ctype.contains("multipart/form-data")) {
                sendText(ex, 400, "{\"error\":\"expected multipart/form-data with field 'game'\"}",
                        "application/json; charset=utf-8");
                return;
            }
            String boundary = boundaryOf(ctype);
            if (boundary == null) {
                sendText(ex, 400, "{\"error\":\"missing multipart boundary\"}",
                        "application/json; charset=utf-8");
                return;
            }
            int contentLen = contentLength(ex);
            if (contentLen > MAX_BODY) {
                sendText(ex, 413, "{\"error\":\"file too large (max ~30MB)\"}",
                        "application/json; charset=utf-8");
                return;
            }
            byte[] body;
            try {
                body = readLimited(ex.getRequestBody(), MAX_BODY + 1024);
            } catch (IOException e) {
                sendText(ex, 413, "{\"error\":\"file too large (max ~30MB)\"}",
                        "application/json; charset=utf-8");
                return;
            }
            if (body.length > MAX_BODY) {
                sendText(ex, 413, "{\"error\":\"file too large (max ~30MB)\"}",
                        "application/json; charset=utf-8");
                return;
            }
            try {
                Part game = null;
                String copiesStr = null;
                for (Part p : parseMultipart(body, boundary)) {
                    if (p.name.equals("game")) game = p;
                    else if (p.name.equals("copies")) copiesStr = p.textValue();
                }
                if (game == null || game.data == null || game.data.length == 0) {
                    sendText(ex, 400, "{\"error\":\"missing file field 'game'\"}",
                            "application/json; charset=utf-8");
                    return;
                }
                int copies = 6;
                if (copiesStr != null && !copiesStr.trim().isEmpty()) {
                    copies = Integer.parseInt(copiesStr.trim());
                }
                if (copies < 1 || copies > MAX_COPIES) {
                    sendText(ex, 400, "{\"error\":\"copies must be 1.." + MAX_COPIES + "\"}",
                            "application/json; charset=utf-8");
                    return;
                }
                String origName = (game.fileName != null && !game.fileName.isEmpty()) ? game.fileName : "game.jar";
                if (!origName.toLowerCase().endsWith(".jar")) origName += ".jar";

                List<byte[]> jars = new ArrayList<byte[]>();
                jars.add(game.data);
                List<String> names = new ArrayList<String>();
                names.add(origName);
                // Silence per-request merge chatter; keep server log to one line.
                PrintStream prev = MergerCLI.LOG;
                MergerCLI.LOG = new PrintStream(new ByteArrayOutputStream());
                byte[] result;
                try {
                    result = MergerCLI.merge(jars, names, copies);
                } finally {
                    MergerCLI.LOG = prev;
                }
                String outName = MergerCLI.deriveOutputName(origName, copies);
                ex.getResponseHeaders().set("Content-Type", "application/java-archive");
                ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + outName + "\"");
                ex.sendResponseHeaders(200, result.length);
                OutputStream out = ex.getResponseBody();
                out.write(result);
                out.close();
                System.out.println("[web] merged " + origName + " x" + copies + " -> " + outName
                        + " (" + result.length + " bytes)");
            } catch (NumberFormatException e) {
                sendText(ex, 400, "{\"error\":\"copies must be a number\"}",
                        "application/json; charset=utf-8");
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                msg = msg.replace("\\", "\\\\").replace("\"", "'");
                sendText(ex, 422, "{\"error\":\"merge failed: " + msg + "\"}",
                        "application/json; charset=utf-8");
                System.out.println("[web] merge failed: " + e);
            }
        }
    }

    // ---- multipart (minimal, Java 8, no deps) ----

    static class Part {
        String name;
        String fileName;
        byte[] data;
        String textValue() {
            if (data == null) return null;
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    static String boundaryOf(String ctype) {
        for (String seg : ctype.split(";")) {
            seg = seg.trim();
            if (seg.startsWith("boundary=")) {
                String b = seg.substring("boundary=".length()).trim();
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() > 1) b = b.substring(1, b.length() - 1);
                return b;
            }
        }
        return null;
    }

    static int contentLength(HttpExchange ex) {
        try {
            String v = ex.getRequestHeaders().getFirst("Content-Length");
            if (v == null) v = ex.getRequestHeaders().getFirst("content-length");
            return v == null ? -1 : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n, total = 0;
        while ((n = in.read(tmp)) > 0) {
            total += n;
            if (total > max) throw new IOException("body too large");
            b.write(tmp, 0, n);
        }
        return b.toByteArray();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) b.write(tmp, 0, n);
        return b.toByteArray();
    }

    static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = from; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    static List<Part> parseMultipart(byte[] body, String boundary) throws IOException {
        List<Part> parts = new ArrayList<Part>();
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] endDelim = ("--" + boundary + "--").getBytes(StandardCharsets.ISO_8859_1);
        int pos = 0;
        while (true) {
            int start = indexOf(body, delim, pos);
            if (start < 0) break;
            // reached final delimiter -> done
            if (indexOf(body, endDelim, start) == start) break;
            int lineEnd = indexOf(body, new byte[]{'\r', '\n'}, start);
            if (lineEnd < 0) break;
            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, lineEnd);
            if (headerEnd < 0) break;
            String headers = new String(body, lineEnd + 2, headerEnd - (lineEnd + 2), StandardCharsets.ISO_8859_1);
            int dataStart = headerEnd + 4;
            int next = indexOf(body, delim, dataStart);
            if (next < 0) break;
            int dataEnd = next - 2; // strip trailing CRLF
            if (dataEnd < dataStart) dataEnd = dataStart;
            Part p = new Part();
            for (String hl : headers.split("\r\n")) {
                String low = hl.toLowerCase(Locale.US);
                if (low.startsWith("content-disposition")) {
                    p.name = paramOf(hl, "name");
                    p.fileName = paramOf(hl, "filename");
                    if (p.fileName != null) {
                        // IE-style full paths -> basename
                        p.fileName = p.fileName.replace('\\', '/');
                        int s = p.fileName.lastIndexOf('/');
                        if (s >= 0) p.fileName = p.fileName.substring(s + 1);
                    }
                }
            }
            if (p.name != null) {
                p.data = Arrays.copyOfRange(body, dataStart, dataEnd);
                parts.add(p);
            }
            pos = next;
        }
        return parts;
    }

    static String paramOf(String header, String key) {
        String q = key + "=\"";
        int i = header.indexOf(q);
        if (i < 0) {
            // unquoted fallback
            q = key + "=";
            i = header.indexOf(q);
            if (i < 0) return null;
            int s = i + q.length();
            int e = header.indexOf(';', s);
            if (e < 0) e = header.length();
            return header.substring(s, e).trim();
        }
        int s = i + q.length();
        int e = header.indexOf('"', s);
        if (e < 0) return null;
        return header.substring(s, e);
    }

    static void sendText(HttpExchange ex, int code, String text, String contentType) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, data.length);
        OutputStream out = ex.getResponseBody();
        out.write(data);
        out.close();
    }
}
