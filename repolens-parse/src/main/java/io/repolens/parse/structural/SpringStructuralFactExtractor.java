package io.repolens.parse.structural;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring MVC / stereotype facts for sequence, DFD, and use-case diagrams,
 * plus first-class Endpoint facts (ADR-012).
 */
public final class SpringStructuralFactExtractor {

    private static final Pattern REST_CONTROLLER = Pattern.compile("@(?:RestController|Controller)\\b");
    private static final Pattern CLASS_REQUEST_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern MAPPING_ANNOTATION = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch|Request)Mapping\\b");
    private static final Pattern NAMED_PATH = Pattern.compile(
            "(?:value|path)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern POSITIONAL_PATH = Pattern.compile("^\\s*\"([^\"]*)\"");
    private static final Pattern SINGLE_REQUEST_METHOD = Pattern.compile(
            "method\\s*=\\s*RequestMethod\\.([A-Z]+)\\b");
    private static final Pattern MULTI_REQUEST_METHOD = Pattern.compile(
            "method\\s*=\\s*\\{");
    private static final Pattern SERVICE = Pattern.compile("@Service\\b");
    private static final Pattern REPOSITORY = Pattern.compile(
            "@Repository\\b|(?:extends|implements)\\s+\\w*Repository\\b");

    private SpringStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source, Symbol owner) {
        if (sink.factsFull() && sink.endpointsFull()) {
            return;
        }
        String ownerId = owner == null ? null : owner.id();
        String ownerName = owner == null ? pathName(path) : owner.name();

        boolean rest = REST_CONTROLLER.matcher(source).find();
        boolean service = SERVICE.matcher(source).find() || ownerName.endsWith("Service");
        boolean repository = REPOSITORY.matcher(source).find() || ownerName.endsWith("Repository");

        if (rest) {
            if (!sink.factsFull()) {
                sink.add("sequence", "controller", ownerName, ownerId, null,
                        "role=controller", path, null);
                sink.add("dfd", "process", ownerName, ownerId, null,
                        "boundary=api", path, null);
            }

            String classPrefix = firstClassMapping(source);
            Matcher mappings = METHOD_MAPPING.matcher(source);
            while (mappings.find() && !sink.factsFull()) {
                String route = combineRoutes(classPrefix, mappings.group(1));
                if (isLikelyClassOnlyMapping(source, mappings.start(), classPrefix, mappings.group(1))) {
                    continue;
                }
                String useCase = humanizeRoute(route, ownerName);
                sink.add("usecase", "use_case", useCase, ownerId, null,
                        "route=" + route, path, StructuralFactSink.lineOf(source, mappings.start()));
                sink.add("usecase", "actor_link", "User->" + useCase,
                        null, ownerId, "actor=User", path, StructuralFactSink.lineOf(source, mappings.start()));
            }

            extractEndpoints(sink, path, source, owner, classPrefix);
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

    private static void extractEndpoints(
            StructuralFactSink sink,
            String path,
            String source,
            Symbol owner,
            String classPrefix
    ) {
        Matcher annotations = MAPPING_ANNOTATION.matcher(source);
        while (annotations.find() && !sink.endpointsFull()) {
            ParsedMapping mapping = parseMapping(source, annotations);
            if (mapping == null) {
                continue;
            }
            if (isLikelyClassOnlyMapping(source, mapping.start(), classPrefix, mapping.rawPath())) {
                continue;
            }
            String route = combineRoutes(classPrefix, mapping.rawPath());
            if (route == null || route.isBlank()) {
                continue;
            }
            int line = StructuralFactSink.lineOf(source, mapping.start());
            int column = columnOf(source, mapping.start());
            SourceLocation location = new SourceLocation(
                    path, line, column, line, column + mapping.annotationLength());
            Optional<String> handlerId = resolveHandler(sink, owner, line);
            Evidence evidence = new Evidence(
                    InferenceMethod.ANNOTATION,
                    Optional.of(location),
                    Optional.empty(),
                    Optional.of("@" + mapping.annotationName() + "Mapping")
            );
            sink.addEndpoint(new Endpoint(
                    "pending",
                    mapping.httpMethod(),
                    route,
                    owner == null ? Optional.empty() : Optional.of(owner.id()),
                    handlerId,
                    location,
                    evidence
            ));
        }
    }

    static ParsedMapping parseMapping(String source, Matcher annotations) {
        String verb = annotations.group(1);
        String annotationName = verb;
        int start = annotations.start();
        int nameEnd = annotations.end();
        int cursor = skipWhitespace(source, nameEnd);
        String attrs = "";
        if (cursor < source.length() && source.charAt(cursor) == '(') {
            int close = findMatchingParen(source, cursor);
            if (close < 0) {
                return null;
            }
            attrs = source.substring(cursor + 1, close);
        }
        if (isMultiValuePath(attrs) || isAmbiguousRequestMethods(attrs, verb)) {
            return null;
        }
        String rawPath = extractPath(attrs);
        if (rawPath == null) {
            return null;
        }
        String httpMethod = httpMethodFor(verb, attrs);
        if (httpMethod == null) {
            return null;
        }
        return new ParsedMapping(annotationName, rawPath, httpMethod, start, nameEnd - start);
    }

    private static String httpMethodFor(String verb, String attrs) {
        if (!"Request".equals(verb)) {
            return verb.toUpperCase(Locale.ROOT);
        }
        if (isAmbiguousRequestMethods(attrs, verb)) {
            return null;
        }
        Matcher method = SINGLE_REQUEST_METHOD.matcher(attrs);
        if (method.find()) {
            return method.group(1);
        }
        return Endpoint.UNKNOWN_METHOD;
    }

    private static boolean isAmbiguousRequestMethods(String attrs, String verb) {
        if (!"Request".equals(verb)) {
            return false;
        }
        Matcher multi = MULTI_REQUEST_METHOD.matcher(attrs);
        if (!multi.find()) {
            return false;
        }
        int open = multi.end() - 1;
        int close = attrs.indexOf('}', open);
        if (close < 0) {
            return true;
        }
        String body = attrs.substring(open + 1, close);
        long count = Pattern.compile("RequestMethod\\.([A-Z]+)").matcher(body).results().count();
        return count != 1;
    }

    private static boolean isMultiValuePath(String attrs) {
        int brace = indexOfUnquoted(attrs, '{');
        if (brace < 0) {
            return false;
        }
        String before = attrs.substring(0, brace);
        boolean pathArray = before.isBlank()
                || before.trim().matches("(?s).*(?:value|path)\\s*=\\s*");
        if (!pathArray) {
            return false;
        }
        int close = attrs.indexOf('}', brace);
        String body = close < 0 ? attrs.substring(brace) : attrs.substring(brace, close);
        return Pattern.compile("\"[^\"]*\"").matcher(body).results().count() > 1;
    }

    private static String extractPath(String attrs) {
        if (attrs == null || attrs.isBlank()) {
            return "";
        }
        Matcher named = NAMED_PATH.matcher(attrs);
        if (named.find()) {
            return named.group(1);
        }
        Matcher positional = POSITIONAL_PATH.matcher(attrs);
        if (positional.find()) {
            return positional.group(1);
        }
        if (isMultiValuePath(attrs)) {
            return null;
        }
        return "";
    }

    private static Optional<String> resolveHandler(
            StructuralFactSink sink,
            Symbol owner,
            int annotationLine
    ) {
        if (owner == null) {
            return Optional.empty();
        }
        List<Symbol> members = sink.methodsByParent().getOrDefault(owner.id(), List.of());
        return members.stream()
                .filter(symbol -> symbol.kind() == SymbolKind.METHOD)
                .filter(symbol -> symbol.location().startLine() >= annotationLine)
                .min(Comparator.comparingInt(symbol -> symbol.location().startLine()))
                .map(Symbol::id);
    }

    private static String firstClassMapping(String source) {
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
            String fallback = ownerName.replace("Controller", "");
            return fallback.isBlank() ? ownerName : fallback;
        }
        String cleaned = route.trim();
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        if (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String pathName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static int columnOf(String source, int offset) {
        int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        return offset - lineStart + 1;
    }

    private static int skipWhitespace(String source, int index) {
        int i = index;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        return i;
    }

    static int findMatchingParen(String source, int openIdx) {
        int depth = 0;
        boolean inString = false;
        for (int i = openIdx; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexOfUnquoted(String attrs, char target) {
        boolean inString = false;
        for (int i = 0; i < attrs.length(); i++) {
            char c = attrs.charAt(i);
            if (c == '"' && (i == 0 || attrs.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && c == target) {
                return i;
            }
        }
        return -1;
    }

    record ParsedMapping(
            String annotationName,
            String rawPath,
            String httpMethod,
            int start,
            int annotationLength
    ) {
    }
}
