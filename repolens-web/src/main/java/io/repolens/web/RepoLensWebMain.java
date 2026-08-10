package io.repolens.web;

/**
 * Standalone entrypoint for the RepoLens Web API.
 */
public final class RepoLensWebMain {

    private RepoLensWebMain() {
    }

    public static void main(String[] args) {
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }
        RepoLensServer server = RepoLensServer.createDefault(port);
        server.start();
        System.out.println("RepoLens Web API listening on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }
}
