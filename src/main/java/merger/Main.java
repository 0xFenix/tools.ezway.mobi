package merger;

import classes.MergerCLI;

/** Single entry point: `java -jar merger.jar` (no args) opens GUI, otherwise runs CLI. */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            MergerGUI.launch();
            return;
        }
        if (args.length == 1 && (args[0].equals("--gui") || args[0].equals("-g"))) {
            MergerGUI.launch();
            return;
        }
        if (args.length >= 1 && args[0].equals("--serve")) {
            int port = 8080;
            for (int i = 1; i < args.length; i++) {
                if ((args[i].equals("--port") || args[i].equals("-p")) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                }
            }
            WebServer.start(port);
            return;
        }
        MergerCLI.main(args);
    }
}
