package merger;

import classes.MergerCLI;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Swing GUI for the J2ME multi-game merger. Shares the same core as the CLI. */
public class MergerGUI extends JFrame {
    private final DefaultListModel<String> inputModel = new DefaultListModel<String>();
    private final JList<String> inputList = new JList<String>(inputModel);
    private final JTextField outField = new JTextField(28);
    private final JSpinner copiesSpin = new JSpinner(new SpinnerNumberModel(10, 1, 25, 1));
    private final JTextField nameField = new JTextField("Multi Menu", 16);
    private final JTextField vendorField = new JTextField("MZMOD", 16);
    private final JTextField displayField = new JTextField("Multi Menu", 16);
    private final JTextField iconField = new JTextField(20);
    private final JTextField emuField = new JTextField("Microemulator.jar", 20);
    private final JTextArea logArea = new JTextArea(14, 60);
    private final JButton mergeBtn = new JButton("Merge");
    private final JButton runBtn = new JButton("Run in Microemulator");
    private String lastOut = null;

    public MergerGUI() {
        super("J2ME Merger — multi-game menu builder");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // ---- inputs ----
        JPanel top = new JPanel(new BorderLayout(6, 6));
        inputList.setVisibleRowCount(5);
        top.add(new JLabel("Game jars (mỗi file nhân thành N bản theo Copies):"), BorderLayout.NORTH);
        top.add(new JScrollPane(inputList), BorderLayout.CENTER);
        JPanel ibtns = new JPanel(new GridLayout(3, 1, 4, 4));
        JButton add = new JButton("Add...");
        JButton del = new JButton("Remove");
        JButton clr = new JButton("Clear");
        ibtns.add(add); ibtns.add(del); ibtns.add(clr);
        top.add(ibtns, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // ---- options ----
        JPanel opts = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int r = 0;
        c.gridx = 0; c.gridy = r; c.weightx = 0; opts.add(new JLabel("Output jar:"), c);
        c.gridx = 1; c.weightx = 1; opts.add(outField, c);
        c.gridx = 2; c.weightx = 0; JButton browseOut = new JButton("Browse...");
        opts.add(browseOut, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("Copies (1-25):"), c);
        c.gridx = 1; opts.add(copiesSpin, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("MIDlet-Name:"), c);
        c.gridx = 1; opts.add(nameField, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("Vendor:"), c);
        c.gridx = 1; opts.add(vendorField, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("Menu title:"), c);
        c.gridx = 1; opts.add(displayField, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("Menu icon (png):"), c);
        c.gridx = 1; opts.add(iconField, c);
        c.gridx = 2; JButton browseIcon = new JButton("Browse...");
        opts.add(browseIcon, c); r++;
        c.gridx = 0; c.gridy = r; opts.add(new JLabel("Microemulator:"), c);
        c.gridx = 1; opts.add(emuField, c);
        c.gridx = 2; JButton browseEmu = new JButton("Browse...");
        opts.add(browseEmu, c);
        add(opts, BorderLayout.CENTER);

        // ---- bottom: buttons + log ----
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        JPanel bbtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bbtns.add(mergeBtn);
        runBtn.setEnabled(false);
        bbtns.add(runBtn);
        bottom.add(bbtns, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bottom.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ---- actions ----
        add.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(".");
                fc.setMultiSelectionEnabled(true);
                fc.setFileFilter(new FileNameExtensionFilter("J2ME jars", "jar"));
                if (fc.showOpenDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION)
                    for (java.io.File f : fc.getSelectedFiles()) inputModel.addElement(f.getAbsolutePath());
            }
        });
        del.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                for (String s : inputList.getSelectedValuesList()) inputModel.removeElement(s);
            }
        });
        clr.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { inputModel.clear(); }
        });
        browseOut.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(".");
                fc.setFileFilter(new FileNameExtensionFilter("JAR", "jar"));
                if (fc.showSaveDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION) {
                    String p = fc.getSelectedFile().getAbsolutePath();
                    if (!p.toLowerCase().endsWith(".jar")) p += ".jar";
                    outField.setText(p);
                }
            }
        });
        browseIcon.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(".");
                fc.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
                if (fc.showOpenDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION)
                    iconField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        browseEmu.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(".");
                fc.setFileFilter(new FileNameExtensionFilter("JAR", "jar"));
                if (fc.showOpenDialog(MergerGUI.this) == JFileChooser.APPROVE_OPTION)
                    emuField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        mergeBtn.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doMerge(); }
        });
        runBtn.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doRun(); }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void doMerge() {
        if (inputModel.isEmpty()) { log("Add at least one game jar first."); return; }
        if (outField.getText().trim().isEmpty()) { log("Choose an output jar first."); return; }
        mergeBtn.setEnabled(false);
        runBtn.setEnabled(false);
        logArea.setText("");
        final List<String> args = new ArrayList<String>();
        args.add("--out"); args.add(outField.getText().trim());
        args.add("--copies"); args.add(copiesSpin.getValue().toString());
        args.add("--name"); args.add(nameField.getText());
        args.add("--vendor"); args.add(vendorField.getText());
        args.add("--display"); args.add(displayField.getText());
        if (!iconField.getText().trim().isEmpty()) { args.add("--icon"); args.add(iconField.getText().trim()); }
        for (int i = 0; i < inputModel.size(); i++) args.add(inputModel.get(i));

        // Redirect core logs into the GUI log panel
        final PrintStream guiLog = new PrintStream(new OutputStream() {
            private final StringBuilder sb = new StringBuilder();
            public void write(int b) {
                if (b == '\n') {
                    final String line = sb.toString();
                    sb.setLength(0);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() { logArea.append(line + "\n"); }
                    });
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
                try {
                    if (get() == 0) {
                        lastOut = outField.getText().trim();
                        runBtn.setEnabled(true);
                        log("DONE: " + lastOut);
                    } else log("FAILED — see log above.");
                } catch (Exception ex) { log("FAILED: " + ex.getMessage()); }
            }
        }.execute();
    }

    private void doRun() {
        if (lastOut == null) return;
        try {
            new ProcessBuilder("java", "-jar", emuField.getText().trim(), lastOut).start();
            log("Launched Microemulator with " + lastOut);
        } catch (Exception ex) { log("Cannot launch emulator: " + ex.getMessage()); }
    }

    private void log(String s) { logArea.append(s + "\n"); }

    public static void launch() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { new MergerGUI().setVisible(true); }
        });
    }
}
