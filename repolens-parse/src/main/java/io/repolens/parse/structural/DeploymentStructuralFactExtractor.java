package io.repolens.parse.structural;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deployment facts from Docker Compose, Dockerfile, and Kubernetes manifests.
 * Does not invent service links from declaration order.
 */
public final class DeploymentStructuralFactExtractor {

    private static final Pattern DOCKER_SERVICE = Pattern.compile("(?m)^\\s{2}([A-Za-z0-9_.-]+):\\s*$");
    private static final Pattern DOCKER_IMAGE = Pattern.compile("(?m)^\\s+image:\\s*[\"']?([^\"'\\s]+)");
    private static final Pattern DEPENDS_ON_LINE = Pattern.compile(
            "(?m)^(\\s{2})([A-Za-z0-9_.-]+):\\s*$([\\s\\S]*?)(?=^\\s{2}[A-Za-z0-9_.-]+:\\s*$|\\Z)");
    private static final Pattern DEPENDS_ON_LIST = Pattern.compile(
            "(?ms)^\\s+depends_on:\\s*(?:\\n(\\s+-\\s+[^\\n]+(?:\\n\\s+-\\s+[^\\n]+)*)|\\[([^\\]]+)\\])");
    private static final Pattern DEPENDS_ON_ITEM = Pattern.compile("-\\s+[\"']?([A-Za-z0-9_.-]+)[\"']?");
    private static final Pattern DOCKERFILE_FROM = Pattern.compile("(?im)^FROM\\s+(\\S+)");
    private static final Pattern K8S_KIND = Pattern.compile("(?m)^kind:\\s*(\\w+)");
    private static final Pattern K8S_NAME = Pattern.compile("(?m)^\\s+name:\\s*[\"']?([^\"'\\s]+)");

    private DeploymentStructuralFactExtractor() {
    }

    public static void extractCompose(StructuralFactSink sink, String path, String source) {
        Matcher services = DOCKER_SERVICE.matcher(source);
        List<String> names = new ArrayList<>();
        while (services.find() && !sink.factsFull()) {
            String name = services.group(1);
            if (Set.of("services", "volumes", "networks", "version", "configs", "secrets").contains(name)) {
                continue;
            }
            names.add(name);
            String kind = inferDeployKind(name);
            sink.add("deployment", kind, name, null, null,
                    "source=compose", path, StructuralFactSink.lineOf(source, services.start()));
        }
        Matcher images = DOCKER_IMAGE.matcher(source);
        while (images.find() && !sink.factsFull()) {
            sink.add("deployment", "image", images.group(1), null, null,
                    "source=compose-image", path, StructuralFactSink.lineOf(source, images.start()));
        }
        emitDependsOnLinks(sink, path, source, names);
    }

    private static void emitDependsOnLinks(
            StructuralFactSink sink,
            String path,
            String source,
            List<String> knownServices
    ) {
        Set<String> known = Set.copyOf(knownServices);
        Matcher blocks = DEPENDS_ON_LINE.matcher(source);
        while (blocks.find() && !sink.factsFull()) {
            String service = blocks.group(2);
            if (!known.contains(service)) {
                continue;
            }
            String body = blocks.group(3);
            Matcher depends = DEPENDS_ON_LIST.matcher(body);
            if (!depends.find()) {
                continue;
            }
            String listBody = depends.group(1);
            String bracket = depends.group(2);
            String scan = listBody != null ? listBody : (bracket == null ? "" : bracket);
            Matcher items = DEPENDS_ON_ITEM.matcher(scan);
            while (items.find() && !sink.factsFull()) {
                String target = items.group(1);
                if (known.contains(target)) {
                    sink.add("deployment", "links", service + "->" + target, null, null,
                            "evidence=depends_on", path, StructuralFactSink.lineOf(source, blocks.start()));
                }
            }
        }
    }

    public static void extractDockerfile(StructuralFactSink sink, String path, String source) {
        Matcher from = DOCKERFILE_FROM.matcher(source);
        while (from.find() && !sink.factsFull()) {
            sink.add("deployment", "container", from.group(1), null, null,
                    "source=dockerfile", path, StructuralFactSink.lineOf(source, from.start()));
        }
    }

    public static void extractKubernetes(StructuralFactSink sink, String path, String source) {
        if (!source.contains("kind:")) {
            return;
        }
        Matcher kind = K8S_KIND.matcher(source);
        Matcher name = K8S_NAME.matcher(source);
        String kindName = kind.find() ? kind.group(1) : "Resource";
        String resourceName = name.find() ? name.group(1) : kindName;
        sink.add("deployment", "k8s_" + kindName.toLowerCase(Locale.ROOT),
                resourceName, null, null, "kind=" + kindName, path, null);
    }

    public static boolean isComposeFile(String lower) {
        String name = Path.of(lower).getFileName().toString();
        return name.equals("docker-compose.yml")
                || name.equals("docker-compose.yaml")
                || name.equals("compose.yml")
                || name.equals("compose.yaml");
    }

    private static String inferDeployKind(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("redis") || lower.contains("cache")) {
            return "cache";
        }
        if (lower.contains("postgres") || lower.contains("mysql") || lower.contains("mongo")
                || lower.contains("db") || lower.contains("database")) {
            return "database";
        }
        if (lower.contains("kafka") || lower.contains("rabbit") || lower.contains("queue")) {
            return "message_broker";
        }
        return "service";
    }
}
