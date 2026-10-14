package merger;

/** Web entry point: `java -jar merger-web.jar [--port 8080]`. */
public class WebMain {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("--port") || args[i].equals("-p")) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--help") || args[i].equals("-h")) {
                System.out.println("Usage: java -jar merger-web.jar [--port 8080]");
                return;
            }
        }
        WebServer.start(port);
    }
}
