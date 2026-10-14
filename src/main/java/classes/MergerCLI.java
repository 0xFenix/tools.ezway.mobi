package classes;

import java.io.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.*;

/**
 * J2ME multi-game merger CLI ported from Advanced-Menu MIDlet (classes.class_a).
 * Reuses original transformer bytecode (class_t, class_k, class_c, class_l) via classpath.
 * Run: java -jar merger.jar --out multi.jar game1.jar game2.jar [options]
 *
 * Core merge logic works on byte[] so the web backend can reuse it without files.
 */
public class MergerCLI {
    /** Log sink; GUI redirects this to its log panel. */
    public static PrintStream LOG = System.out;

    static class GameInfo {
        String path;
        byte[] manifestBytes;
        Manifest manifest;
        List<String> entryNames = new ArrayList<String>();
        Map<String, byte[]> entryBytes = new HashMap<String, byte[]>();
        class_c cc;
    }

    // Mirrors class_a.a(byte[], char): CAFEBABE check + class_t transform
    static final class_t transformer = new class_t();
    static final ByteArrayOutputStream wBuf = new ByteArrayOutputStream();
    static final DataOutputStream xOut = new DataOutputStream(wBuf);

    static synchronized byte[] transformClass(byte[] code, char prefix) {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        try {
            if (in.readInt() != -889275714) { // 0xCAFEBABE
                try { in.close(); } catch (Exception e) {}
                return code;
            }
        } catch (Exception e) {
            return code;
        }
        transformer.a(in);
        wBuf.reset();
        transformer.a(in, prefix);
        try {
            transformer.a(xOut);
            xOut.flush();
        } catch (Exception e) {
            return code;
        }
        return wBuf.toByteArray();
    }

    // Mirrors class_a.b(byte[]): patch javax/microedition/lcdui/List -> lib/List in MZMOD.class
    static int patchMZMOD(byte[] b) {
        int len = b.length;
        int end = len - 35;
        for (int p = 10; p < end; p++) {
            if (b[p]==106&&b[p+1]==97&&b[p+2]==118&&b[p+3]==97&&b[p+4]==120
              &&b[p+5]==47&&b[p+6]==109&&b[p+7]==105&&b[p+8]==99&&b[p+9]==114
              &&b[p+10]==111&&b[p+11]==101&&b[p+12]==100&&b[p+13]==105&&b[p+14]==116
              &&b[p+15]==105&&b[p+16]==111&&b[p+17]==110&&b[p+18]==47&&b[p+19]==108
              &&b[p+20]==99&&b[p+21]==100&&b[p+22]==117&&b[p+23]==105&&b[p+24]==47
              &&b[p+25]==76&&b[p+26]==105&&b[p+27]==115&&b[p+28]==116) {
                b[p]='l'; b[p+1]='i'; b[p+2]='b';
                int q = p-1;
                while (b[q] != 1) q--;
                int slen = b[q+2] & 255;
                b[q+2] = (byte)(slen-21);
                System.arraycopy(b, p+24, b, p+3, len-p-24);
                len -= 21; end -= 21;
            }
        }
        return len;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) b.write(tmp, 0, n);
        return b.toByteArray();
    }

    static GameInfo loadGameBytes(String displayName, byte[] jarBytes) throws IOException {
        GameInfo g = new GameInfo();
        g.path = displayName;
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarBytes));
        ZipEntry e;
        byte[] mfBytes = null;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName();
            byte[] data = readAll(zin);
            g.entryBytes.put(name, data);
            g.entryNames.add(name);
            if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) mfBytes = data;
        }
        zin.close();
        if (mfBytes == null) throw new IOException("No MANIFEST.MF in " + displayName);
        g.manifestBytes = mfBytes;
        g.manifest = new Manifest(new ByteArrayInputStream(mfBytes));
        Vector v = new Vector();
        v.addElement(new class_l("META-INF/MANIFEST.MF"));
        for (String n : g.entryNames) {
            if (n.equals("META-INF/MANIFEST.MF")) continue;
            if (n.endsWith("/")) continue;
            v.addElement(new class_l(n));
        }
        g.cc = new class_c(displayName, v);
        if (!g.cc.a(mfBytes)) throw new IOException("class_c manifest parse failed: " + displayName);
        return g;
    }

    static GameInfo loadGame(String path) throws IOException {
        FileInputStream fin = new FileInputStream(path);
        byte[] data = readAll(fin);
        fin.close();
        return loadGameBytes(path, data);
    }

    static Map<String, byte[]> readNestedJar(byte[] nestedJarBytes) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<String, byte[]>();
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(nestedJarBytes));
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            m.put(e.getName(), readAll(zin));
        }
        zin.close();
        return m;
    }

    /** Strip .jar and directories: "C:/a/Avatar.jar" -> "Avatar". */
    public static String deriveBaseName(String fileName) {
        if (fileName == null) return "game";
        String b = fileName.replace('\\', '/');
        int s = b.lastIndexOf('/');
        if (s >= 0) b = b.substring(s + 1);
        if (b.toLowerCase().endsWith(".jar")) b = b.substring(0, b.length() - 4);
        if (b.isEmpty()) b = "game";
        return b;
    }

    /** "<base>-menu-xN.jar" — same rule as the desktop GUI. */
    public static String deriveOutputName(String inputFileName, int copies) {
        return deriveBaseName(inputFileName) + "-menu-x" + copies + ".jar";
    }

    /** Merge with auto manifest (name/vendor/display derived from first jar). */
    public static byte[] merge(List<byte[]> jarBytesList, List<String> jarNames, int copies) throws Exception {
        return merge(jarBytesList, jarNames, copies, null, null, null, null);
    }

    /**
     * Merge core working purely on bytes — shared by desktop CLI/GUI and the web backend.
     * Overrides (may be null): midName, vendor, display, iconBytes.
     */
    public static byte[] merge(List<byte[]> jarBytesList, List<String> jarNames, int copies,
                               String midNameOv, String vendorOv, String displayOv,
                               byte[] iconOv) throws Exception {
        if (jarBytesList == null || jarBytesList.isEmpty()) throw new IOException("No input jars");
        if (copies < 1 || copies > 25) throw new IOException("--copies must be 1..25");

        LOG.println("[merger] inputs=" + jarNames);
        List<GameInfo> games = new ArrayList<GameInfo>();
        for (int i = 0; i < jarBytesList.size(); i++) {
            String nm = (jarNames != null && i < jarNames.size()) ? jarNames.get(i) : ("game" + i + ".jar");
            GameInfo g = loadGameBytes(nm, jarBytesList.get(i));
            LOG.println("[merger] loaded " + nm + " MIDlet-1=" + g.manifest.getMainAttributes().getValue("MIDlet-1"));
            games.add(g);
        }

        // Auto manifest: derive from first input jar unless overridden
        String midName = midNameOv, vendor = vendorOv, display = displayOv;
        String inName = games.get(0).manifest.getMainAttributes().getValue("MIDlet-Name");
        if (inName == null || inName.trim().isEmpty()) inName = "Multi Menu";
        inName = inName.trim();
        if (midName == null) midName = inName + "-x" + copies;
        if (vendor == null) vendor = "avt.ezway.mobi";
        if (display == null) display = inName + " Adv Menu";
        LOG.println("[merger] manifest: name=" + midName + " vendor=" + vendor + " display=" + display);

        // Load runtime stub from embedded resources (fat jar)
        byte[] libsBytes = null, ssBytes = null, defaultIcon = null;
        InputStream res = MergerCLI.class.getResourceAsStream("/MZMODlibs.jar");
        if (res == null) throw new IOException("Missing embedded runtime: /MZMODlibs.jar");
        LOG.println("[merger] runtime from embedded resources");
        libsBytes = readAll(res); res.close();
        res = MergerCLI.class.getResourceAsStream("/MZMODSSlib.jar");
        ssBytes = readAll(res); res.close();
        res = MergerCLI.class.getResourceAsStream("/icon.png");
        if (res != null) { defaultIcon = readAll(res); res.close(); }
        Map<String, byte[]> libsMap = readNestedJar(libsBytes);
        Map<String, byte[]> ssMap = readNestedJar(ssBytes);

        Set<String> written = new HashSet<String>();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ZipOutputStream zout = new ZipOutputStream(bout);
        Vector A = new Vector();
        int D = 0;

        // icon for output menu
        byte[] menuIcon = (iconOv != null) ? iconOv : defaultIcon;
        writeEntry(zout, written, "MZMOD/mid.png", menuIcon);

        // runtime: MZMODlibs.jar classes (shims + MidletInfo) + SSlib classes (menu + lib wrappers)
        for (Map.Entry<String,byte[]> e : libsMap.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            writeEntry(zout, written, e.getKey(), e.getValue());
        }
        for (Map.Entry<String,byte[]> e : ssMap.entrySet()) {
            String n = e.getKey();
            if (!n.endsWith(".class")) continue;
            if (written.contains(n)) continue;
            byte[] data = e.getValue();
            if (n.endsWith("MZMOD/MZMOD.class")) {
                byte[] copy = Arrays.copyOf(data, data.length);
                int nl = patchMZMOD(copy);
                data = Arrays.copyOf(copy, nl);
                LOG.println("[merger] patched MZMOD.class " + data.length);
            }
            writeEntry(zout, written, n, data);
        }

        // games: ONE namespace per input jar (original MIDlet hardcoded 10 copies; fixed here)
        for (GameInfo g : games) {
            // actual MIDlet-N entries from manifest
            List<String[]> midlets = new ArrayList<String[]>(); // {display, icon, class}
            for (int n = 1; n <= 25; n++) {
                String v = g.manifest.getMainAttributes().getValue("MIDlet-" + n);
                if (v == null) break;
                String[] parts = v.split(",", 3);
                if (parts.length == 3) midlets.add(new String[]{parts[0].trim(), parts[1].trim(), parts[2].trim()});
            }
            if (midlets.isEmpty()) throw new IOException("No MIDlet-1 in " + g.path);
            LOG.println("[merger] " + g.path + " midlets=" + midlets.size() + " icon=" + g.cc.d + " newIcon=" + g.cc.e);
            // .ini content = original manifest text
            String iniText = new String(g.manifestBytes, "UTF-8");
            writeEntry(zout, written, g.cc.f, iniText.getBytes("UTF-8"));
            List<String> classEntries = new ArrayList<String>();
            for (String n : g.entryNames) if (n.endsWith(".class")) classEntries.add(n);
            for (int k = 0; k < copies; k++) {
                String prefixDir = (D == 0) ? "" : ((char)(97 + D) + "/");
                char prefixChar = prefixDir.isEmpty() ? 'a' : prefixDir.charAt(0);
                String staticName = prefixDir.isEmpty() ? "Static" : (prefixDir.charAt(0) + "/Static");
                Hashtable map = class_t.a();
                for (String n : classEntries) {
                    String base = n.substring(0, n.length() - 6);
                    map.put(base, prefixDir + base);
                }
                for (String n : classEntries) {
                    byte[] raw = g.entryBytes.get(n);
                    byte[] tr = transformClass(raw, prefixChar);
                    writeEntry(zout, written, prefixDir + n, tr);
                }
                byte[] st = class_k.a(class_t.a, staticName);
                if (st != null) writeEntry(zout, written, staticName + ".class", st);
                LOG.println("[merger] copy " + (k+1) + "/" + copies + " namespace '" + prefixDir + "' classes=" + classEntries.size() + " static=" + (st == null ? 0 : st.length));
                // manifest menu entries, one per MIDlet per copy (display "1.Name", "2.Name", ...)
                for (String[] m3 : midlets) {
                    String dotted = prefixDir.isEmpty() ? m3[2] : (prefixDir.charAt(0) + "." + m3[2]);
                    A.addElement((k+1) + "." + m3[0] + ",/" + g.cc.e + "," + dotted + ",/" + g.cc.f);
                }
                D++;
            }
            // resources (non-class), first-wins; duplicate icon as timestamp.png
            for (String n : g.entryNames) {
                if (n.endsWith(".class") || n.equals("META-INF/MANIFEST.MF") || n.endsWith("/")) continue;
                byte[] raw = g.entryBytes.get(n);
                writeEntry(zout, written, n, raw);
                if (g.cc.d != null && n.equals(g.cc.d)) {
                    writeEntry(zout, written, g.cc.e, raw);
                    LOG.println("[merger] icon " + n + " -> " + g.cc.e);
                }
            }
        }

        // manifest
        StringBuilder mf = new StringBuilder();
        mf.append("Manifest-Version: 1.0\r\n");
        mf.append("MicroEdition-Configuration: CLDC-1.1\r\n");
        mf.append("MicroEdition-Profile: MIDP-2.0\r\n");
        mf.append("MIDlet-Version: 2.5\r\n");
        mf.append("MIDlet-Vendor: ").append(vendor).append("\r\n");
        mf.append("MIDlet-Name: ").append(midName).append("\r\n");
        mf.append("MIDlet-1: ").append(display).append(",/MZMOD/mid.png,MZMOD.MZMOD\r\n");
        for (int i = 0; i < A.size(); i++) mf.append(i).append(": ").append(A.elementAt(i)).append("\r\n");
        mf.append("\r\n");
        byte[] mfBytes = mf.toString().getBytes("UTF-8");
        // ZipOutputStream handles manifest as regular entry; write last? must be first ideally, but microemu tolerant.
        // Rebuild: we already wrote entries; manifest must exist — write now (readers scan central dir).
        writeEntry(zout, written, "META-INF/MANIFEST.MF", mfBytes);
        zout.close();
        LOG.println("[merger] BUILT entries=" + written.size() + " games=" + games.size() + " bytes=" + bout.size());
        return bout.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        String out = null, midName = null, vendor = null, display = null, iconPath = null;
        int copies = 1;
        List<String> inputs = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--out") && i + 1 < args.length) out = args[++i];
            else if (args[i].equals("--name") && i + 1 < args.length) midName = args[++i];
            else if (args[i].equals("--vendor") && i + 1 < args.length) vendor = args[++i];
            else if (args[i].equals("--display") && i + 1 < args.length) display = args[++i];
            else if (args[i].equals("--icon") && i + 1 < args.length) iconPath = args[++i];
            else if (args[i].equals("--copies") && i + 1 < args.length) copies = Integer.parseInt(args[++i]);
            else if (args[i].equals("--help") || args[i].equals("-h")) { usage(); return; }
            else if (!args[i].startsWith("--")) inputs.add(args[i]);
        }
        if (out == null || inputs.isEmpty()) { usage(); System.exit(2); }
        if (copies < 1 || copies > 25) { LOG.println("--copies must be 1..25"); System.exit(2); }

        List<byte[]> jarBytes = new ArrayList<byte[]>();
        for (String p : inputs) {
            FileInputStream fin = new FileInputStream(p);
            jarBytes.add(readAll(fin));
            fin.close();
        }
        byte[] iconBytes = null;
        if (iconPath != null) {
            FileInputStream fin = new FileInputStream(iconPath);
            iconBytes = readAll(fin);
            fin.close();
        }
        byte[] result = merge(jarBytes, inputs, copies, midName, vendor, display, iconBytes);
        FileOutputStream fout = new FileOutputStream(out);
        fout.write(result);
        fout.close();
        LOG.println("[merger] WROTE " + out + " bytes=" + result.length);
        LOG.println("[merger] Run: java -jar Microemulator.jar \"" + out + "\"  (GUI)  or  java -cp Microemulator.jar org.microemu.app.Headless --jar \"" + out + "\"");
    }

    static void writeEntry(ZipOutputStream z, Set<String> seen, String name, byte[] data) throws IOException {
        if (data == null) return;
        if (seen.contains(name)) return;
        seen.add(name);
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    static void usage() {
        LOG.println("Usage: java -jar merger.jar --out <game>-menu-xN.jar [--copies N] game.jar [game2.jar ...]");
        LOG.println("       --name/--vendor/--display/--icon override auto manifest (default: derived from input jar)");
        LOG.println("       java -jar merger.jar  (no args: open GUI)");
    }
}
