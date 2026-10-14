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
            System.out.println("Web backend moved to merger-web.jar: java -jar merger-web.jar [--port 8080]");
            return;
        }
        MergerCLI.main(args);
    }
}
