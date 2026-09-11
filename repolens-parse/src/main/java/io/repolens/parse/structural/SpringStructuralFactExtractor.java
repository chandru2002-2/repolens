package io.repolens.parse.structural;

import io.repolens.core.model.Symbol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring MVC / stereotype facts for sequence, DFD, and use-case diagrams.
 */
public final class SpringStructuralFactExtractor {

    private static final Pattern REST_CONTROLLER = Pattern.compile("@(?:RestController|Controller)\\b");
    private static final Pattern CLASS_REQUEST_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern SERVICE = Pattern.compile("@Service\\b");
    private static final Pattern REPOSITORY = Pattern.compile(
            "@Repository\\b|(?:extends|implements)\\s+\\w*Repository\\b");

    private SpringStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source, Symbol owner) {
        if (sink.factsFull()) {
            return;
        }
        String ownerId = owner == null ? null : owner.id();
        String ownerName = owner == null ? pathName(path) : owner.name();

        boolean rest = REST_CONTROLLER.matcher(source).find();
        boolean service = SERVICE.matcher(source).find() || ownerName.endsWith("Service");
        boolean repository = REPOSITORY.matcher(source).find() || ownerName.endsWith("Repository");

        if (rest) {
            sink.add("sequence", "controller", ownerName, ownerId, null,
                    "role=controller", path, null);
            sink.add("dfd", "process", ownerName, ownerId, null,
                    "boundary=api", path, null);

            String classPrefix = firstClassMapping(source);
            Matcher mappings = METHOD_MAPPING.matcher(source);
            while (mappings.find() && !sink.factsFull()) {
                // Skip the class-level @RequestMapping match if METHOD_MAPPING also hits it —
                // RequestMapping is included in METHOD_MAPPING; combine with prefix carefully.
                String route = combineRoutes(classPrefix, mappings.group(1));
                // Avoid double-counting pure class-level RequestMapping as a use case when
                // it is the only mapping and equals classPrefix with no method mappings left.
                if (isLikelyClassOnlyMapping(source, mappings.start(), classPrefix, mappings.group(1))) {
                    continue;
                }
                String useCase = humanizeRoute(route, ownerName);
                sink.add("usecase", "use_case", useCase, ownerId, null,
                        "route=" + route, path, StructuralFactSink.lineOf(source, mappings.start()));
                sink.add("usecase", "actor_link", "User->" + useCase,
                        null, ownerId, "actor=User", path, StructuralFactSink.lineOf(source, mappings.start()));
            }
        }
        if (service) {
            sink.add("sequence", "service", ownerName, ownerId, null,
                    "role=service", path, null);
            sink.add("dfd", "process", ownerName, ownerId, null,
                    "process=service", path, null);
        }
        if (repository) {
            sink.add("sequence", "repository", ownerName, ownerId, null,
                    "role=repository", path, null);
            sink.add("dfd", "data_store", ownerName, ownerId, null,
                    "store=repository", path, null);
        }
    }

    private static String firstClassMapping(String source) {
        // Prefer class-level @RequestMapping appearing before the first method body '{'.
        int classBody = source.indexOf('{');
        String header = classBody > 0 ? source.substring(0, classBody) : source;
        Matcher matcher = CLASS_REQUEST_MAPPING.matcher(header);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean isLikelyClassOnlyMapping(
            String source,
            int matchStart,
            String classPrefix,
            String matchedRoute
    ) {
        if (classPrefix == null || classPrefix.isBlank()) {
            return false;
        }
        int classBody = source.indexOf('{');
        return classBody > 0 && matchStart < classBody && classPrefix.equals(matchedRoute);
    }

    static String combineRoutes(String classPrefix, String methodRoute) {
        String prefix = classPrefix == null ? "" : classPrefix.trim();
        String method = methodRoute == null ? "" : methodRoute.trim();
        if (prefix.isBlank()) {
            return method;
        }
        if (method.isBlank() || "/".equals(method)) {
            return prefix;
        }
        if (prefix.endsWith("/") && method.startsWith("/")) {
            return prefix.substring(0, prefix.length() - 1) + method;
        }
        if (!prefix.endsWith("/") && !method.startsWith("/")) {
            return prefix + "/" + method;
        }
        return prefix + method;
    }

    static String humanizeRoute(String route, String ownerName) {
        if (route == null || route.isBlank() || "/".equals(route)) {
            return ownerName.replace("Controller", "");
        }
        String cleaned = route.replaceAll("[{}]", "").replaceAll("^/+|/+$", "");
        if (cleaned.isBlank()) {
            return ownerName.replace("Controller", "");
        }
        String[] parts = cleaned.split("/");
        String last = parts[parts.length - 1];
        if (last.isBlank()) {
            return ownerName.replace("Controller", "");
        }
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static String pathName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
