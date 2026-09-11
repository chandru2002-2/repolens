package io.repolens.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import io.repolens.api.AnalysisJobDto;
import io.repolens.api.AnalysisResponseDto;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * Thin Javalin HTTP adapter over RepoLens Core (ADR-005).
 * Serves the Web UI from classpath:/public when present.
 */
public final class RepoLensServer implements AutoCloseable {

    private final int port;
    private final AnalysisJobService jobService;
    private final ObjectMapper objectMapper;
    private Javalin app;

    public RepoLensServer(int port, AnalysisJobService jobService, ObjectMapper objectMapper) {
        this.port = port;
        this.jobService = Objects.requireNonNull(jobService, "jobService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public static RepoLensServer createDefault(int port) {
        return new RepoLensServer(port, WebModule.jobService(), new ObjectMapper());
    }

    public void start() {
        if (app != null) {
            return;
        }
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(objectMapper, true));
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                rule.anyHost();
            }));
            if (Thread.currentThread().getContextClassLoader().getResource("public/index.html") != null) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public";
                    staticFiles.location = Location.CLASSPATH;
                });
            }
        });

        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "service", "repolens-web",
                "ui", Thread.currentThread().getContextClassLoader().getResource("public/index.html") != null
        )));

        app.post("/v1/analyze", ctx -> {
            AnalyzeRequest request = ctx.bodyAsClass(AnalyzeRequest.class);
            if (request == null || request.source() == null || request.source().isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "source is required"));
                return;
            }
            try {
                AnalysisJob job = jobService.submit(request.source(), request.remote());
                ctx.status(HttpStatus.ACCEPTED).json(jobService.toDto(job));
            } catch (IllegalArgumentException ex) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", ex.getMessage()));
            }
        });

        app.get("/v1/jobs/{id}", ctx -> {
            String id = ctx.pathParam("id");
            AnalysisJob job = jobService.find(id).orElse(null);
            if (job == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "job not found"));
                return;
            }
            AnalysisJobDto dto = jobService.toDto(job);
            ctx.json(new AnalysisJobDto(
                    dto.id(),
                    dto.status(),
                    dto.source(),
                    dto.remote(),
                    dto.createdAt(),
                    dto.updatedAt(),
                    dto.error(),
                    null
            ));
        });

        app.get("/v1/jobs/{id}/result", ctx -> {
            String id = ctx.pathParam("id");
            AnalysisJob job = jobService.find(id).orElse(null);
            if (job == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "job not found"));
                return;
            }
            if (job.status() == JobStatus.FAILED) {
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(Map.of(
                        "id", job.id(),
                        "status", job.status().name(),
                        "error", job.error()
                ));
                return;
            }
            if (job.status() != JobStatus.COMPLETED || job.result() == null) {
                ctx.status(HttpStatus.CONFLICT).json(Map.of(
                        "id", job.id(),
                        "status", job.status().name(),
                        "message", "result not ready"
                ));
                return;
            }
            ctx.json(job.result());
        });

        app.get("/v1/jobs/{id}/graph", ctx -> {
            String id = ctx.pathParam("id");
            AnalysisJob job = jobService.find(id).orElse(null);
            if (job == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "job not found"));
                return;
            }
            if (job.status() != JobStatus.COMPLETED || job.result() == null) {
                ctx.status(HttpStatus.CONFLICT).json(Map.of(
                        "id", job.id(),
                        "status", job.status().name(),
                        "message", "graph not ready"
                ));
                return;
            }
            AnalysisResponseDto result = job.result();
            ctx.json(result.graph());
        });

        app.error(404, ctx -> {
            if (ctx.path().startsWith("/v1") || ctx.path().equals("/health")) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "not found"));
                return;
            }
            InputStream index = Thread.currentThread().getContextClassLoader().getResourceAsStream("public/index.html");
            if (index == null) {
                ctx.status(HttpStatus.NOT_FOUND).result("RepoLens UI is not packaged. Run the web UI build.");
                return;
            }
            try (index) {
                ctx.contentType("text/html").result(index.readAllBytes());
            }
        });

        // Bind all interfaces so hosted platforms (e.g. Render) can reach the service.
        // Localhost / 127.0.0.1 access continues to work.
        app.start(RepoLensWebMain.BIND_HOST, port);
    }

    public int port() {
        return port;
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
        jobService.close();
    }

    @Override
    public void close() {
        stop();
    }

    public record AnalyzeRequest(String source, boolean remote) {
    }
}
