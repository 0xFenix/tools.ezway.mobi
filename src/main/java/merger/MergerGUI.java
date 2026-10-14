package merger;

import classes.MergerCLI;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Swing GUI for the J2ME multi-game merger. Shares the same core as the CLI. */
public class MergerGUI extends JFrame {
    private final DefaultListModel<String> inputModel = new DefaultListModel<String>();
    private final JList<String> inputList = new JList<String>(inputModel);
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
        bar.add(new JLabel("Game jars (mỗi file nhân thành N bản theo Copies)"));
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel("Giao diện: "));
        bar.add(themeBox);
        add(bar, BorderLayout.NORTH);

        // ---- main panel ----
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        inputList.setVisibleRowCount(5);
        JScrollPane listScroll = new JScrollPane(inputList);
        JPanel ibtns = new JPanel(new GridLayout(3, 1, 4, 4));
        JButton add = new JButton("Add...");
        JButton del = new JButton("Remove");
        JButton clr = new JButton("Clear");
        ibtns.add(add); ibtns.add(del); ibtns.add(clr);
        JPanel listRow = new JPanel(new BorderLayout(6, 0));
        listRow.add(listScroll, BorderLayout.CENTER);
        listRow.add(ibtns, BorderLayout.EAST);
        main.add(listRow, BorderLayout.NORTH);

        JLabel hint = new JLabel("Mỗi game được nhân thành 6 bản, file gộp lưu cùng thư mục với game đầu tiên.");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        main.add(hint, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        // ---- bottom: button + progress + log ----
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        JPanel bbtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
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
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new FileNameExtensionFilter("J2ME jars", "jar"));
            if (fc.showOpenDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION)
                for (java.io.File f : fc.getSelectedFiles()) inputModel.addElement(f.getAbsolutePath());
        });
        del.addActionListener(e -> {
            for (String s : inputList.getSelectedValuesList()) inputModel.removeElement(s);
        });
        clr.addActionListener(e -> inputModel.clear());
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
        if (inputModel.isEmpty()) { log("Add at least one game jar first."); return; }
        final String first = inputModel.get(0);
        final java.io.File firstFile = new java.io.File(first);
        String base = firstFile.getName();
        if (base.toLowerCase().endsWith(".jar")) base = base.substring(0, base.length() - 4);
        java.io.File out = new java.io.File(firstFile.getParent(), base + "-menu.jar");
        int n = 2;
        while (out.exists()) {
            out = new java.io.File(firstFile.getParent(), base + "-menu-" + (n++) + ".jar");
        }
        final String outPath = out.getAbsolutePath();
        mergeBtn.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        logArea.setText("");
        final List<String> args = new ArrayList<String>();
        args.add("--out"); args.add(outPath);
        args.add("--copies"); args.add("6");
        for (int i = 0; i < inputModel.size(); i++) args.add(inputModel.get(i));

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
