package merger;

import classes.MergerCLI;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Swing GUI for the J2ME multi-game merger. Shares the same core as the CLI. */
public class MergerGUI extends JFrame {
    private final JTextField fileField = new JTextField(30);
    private final JSpinner copiesSpin = new JSpinner(new SpinnerNumberModel(6, 1, 25, 1));
    private final JTextArea logArea = new JTextArea(12, 60);
    private final JButton mergeBtn = new JButton("Merge");
    private final JComboBox<String> themeBox = new JComboBox<String>(new String[]{"Sáng", "Tối"});
    private final JProgressBar progress = new JProgressBar();
    private boolean dark = false;

    public MergerGUI() {
        super("J2ME Merger — multi-game menu builder");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // ---- toolbar ----
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        bar.add(new JLabel("Chọn 1 file game jar để gộp"));
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel("Giao diện: "));
        bar.add(themeBox);
        add(bar, BorderLayout.NORTH);

        // ---- main panel ----
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        fileField.setEditable(false);
        fileField.setToolTipText("Kéo-thả file .jar vào đây");
        fileField.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    java.util.List<java.io.File> files =
                        (java.util.List<java.io.File>) s.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (java.io.File f : files)
                        if (f.getName().toLowerCase().endsWith(".jar")) {
                            fileField.setText(f.getAbsolutePath());
                            return true;
                        }
                    return false;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        JButton browse = new JButton("Chọn file...");
        JPanel fileRow = new JPanel(new BorderLayout(6, 0));
        fileRow.add(fileField, BorderLayout.CENTER);
        fileRow.add(browse, BorderLayout.EAST);
        main.add(fileRow, BorderLayout.NORTH);

        JLabel hint = new JLabel("File gộp lưu cùng thư mục: <tên-game>-menu-xN.jar (N = số copies).");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        main.add(hint, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        // ---- bottom: button + progress + log ----
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        JPanel bbtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bbtns.add(new JLabel("Copies (bản/game):"));
        copiesSpin.setPreferredSize(new Dimension(64, copiesSpin.getPreferredSize().height));
        bbtns.add(copiesSpin);
        bbtns.add(mergeBtn);
        bbtns.add(progress);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(160, 16));
        bottom.add(bbtns, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bottom.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(mergeBtn);

        // ---- actions ----
        themeBox.addActionListener(e -> applyTheme(themeBox.getSelectedIndex() == 1));
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            fc.setFileFilter(new FileNameExtensionFilter("J2ME jars", "jar"));
            if (fc.showOpenDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION)
                fileField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        mergeBtn.addActionListener(e -> doMerge());

        pack();
        setMinimumSize(new Dimension(620, 520));
        setLocationRelativeTo(null);
    }

    private void applyTheme(boolean toDark) {
        if (toDark == dark) return;
        dark = toDark;
        try {
            if (dark) UIManager.setLookAndFeel(new FlatDarkLaf());
            else UIManager.setLookAndFeel(new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(this);
            pack();
        } catch (Exception ex) {
            log("Không đổi được theme: " + ex.getMessage());
            dark = !toDark;
            themeBox.setSelectedIndex(dark ? 1 : 0);
        }
    }

    private void doMerge() {
        final String in = fileField.getText().trim();
        if (in.isEmpty()) { log("Chọn 1 file game jar trước."); return; }
        final int copies = (Integer) copiesSpin.getValue();
        final java.io.File inFile = new java.io.File(in);
        String base = inFile.getName();
        if (base.toLowerCase().endsWith(".jar")) base = base.substring(0, base.length() - 4);
        java.io.File out = new java.io.File(inFile.getParent(), base + "-menu-x" + copies + ".jar");
        int n = 2;
        while (out.exists()) {
            out = new java.io.File(inFile.getParent(), base + "-menu-x" + copies + "-" + (n++) + ".jar");
        }
        final String outPath = out.getAbsolutePath();
        mergeBtn.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        logArea.setText("");
        final List<String> args = new ArrayList<String>();
        args.add("--out"); args.add(outPath);
        args.add("--copies"); args.add(String.valueOf(copies));
        args.add(in);

        // Redirect core logs into the GUI log panel
        final PrintStream guiLog = new PrintStream(new OutputStream() {
            private final StringBuilder sb = new StringBuilder();
            public void write(int b) {
                if (b == '\n') {
                    final String line = sb.toString();
                    sb.setLength(0);
                    SwingUtilities.invokeLater(() -> logArea.append(line + "\n"));
                } else sb.append((char) b);
            }
        }, true);
        final PrintStream old = MergerCLI.LOG;
        new SwingWorker<Integer, Void>() {
            protected Integer doInBackground() {
                MergerCLI.LOG = guiLog;
                try {
                    MergerCLI.main(args.toArray(new String[0]));
                    return 0;
                } catch (Exception ex) {
                    ex.printStackTrace(guiLog);
                    return 1;
                } finally {
                    MergerCLI.LOG = old;
                }
            }
            protected void done() {
                mergeBtn.setEnabled(true);
                progress.setVisible(false);
                try {
                    if (get() == 0) {
                        log("DONE: " + outPath);
                    } else log("FAILED — see log above.");
                } catch (Exception ex) { log("FAILED: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void log(String s) { logArea.append(s + "\n"); }

    public static void launch() {
        try { UIManager.setLookAndFeel(new FlatLightLaf()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MergerGUI().setVisible(true));
    }
}
