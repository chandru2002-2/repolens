package io.repolens.web;

/**
 * Standalone entrypoint for the RepoLens Web API.
 *
 * <p>Port resolution (Render-compatible): {@code --port} CLI flag, else {@code PORT}
 * environment variable, else {@code 8080}.
 */
public final class RepoLensWebMain {

    static final String BIND_HOST = "0.0.0.0";
    static final int DEFAULT_PORT = 8080;

    private RepoLensWebMain() {
    }

    public static void main(String[] args) {
        int port = resolvePort(args, System.getenv("PORT"));
        RepoLensServer server = RepoLensServer.createDefault(port);
        server.start();
        System.out.println("RepoLens Web API listening on http://" + BIND_HOST + ":" + port
                + " (local: http://localhost:" + port + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }

    /**
     * Resolves listen port for local and hosted (Render) runs.
     *
     * @param args command-line args (may include {@code --port <n>})
     * @param portEnv value of {@code PORT}, or {@code null} if unset
     */
    static int resolvePort(String[] args, String portEnv) {
        Integer fromArgs = portFromArgs(args);
        if (fromArgs != null) {
            return fromArgs;
        }
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                return Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid PORT environment value: " + portEnv, ex);
            }
        }
        return DEFAULT_PORT;
    }

    private static Integer portFromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid --port value: " + args[i + 1], ex);
                }
            }
        }
        return null;
    }
}
