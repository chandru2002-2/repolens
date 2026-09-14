package io.repolens.parse.structural;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FastAPI {@code @app.get}/{@code post}/{@code put}/… endpoint facts.
 */
public final class FastApiEndpointExtractor {

    private static final Pattern MAPPING = Pattern.compile(
            "@(\\w+)\\.(get|post|put|delete|patch|head|options|trace)\\b",
            Pattern.CASE_INSENSITIVE);

    private FastApiEndpointExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        Matcher matcher = MAPPING.matcher(source);
        while (matcher.find() && !sink.endpointsFull()) {
            String attrs = PythonStructuralFactExtractor.parseDecoratorArgs(source, matcher.end());
            if (attrs == null) {
                continue;
            }
            if (PythonStructuralFactExtractor.extractMethodsKeyword(attrs).isPresent()) {
                continue;
            }
            String route = PythonStructuralFactExtractor.extractSinglePath(attrs);
            if (route == null || route.isBlank()) {
                continue;
            }
            String httpMethod = matcher.group(2).toUpperCase(Locale.ROOT);
            PythonStructuralFactExtractor.emitEndpoint(
                    sink,
                    path,
                    source,
                    matcher.start(),
                    matcher.end() - matcher.start(),
                    httpMethod,
                    route,
                    "@" + matcher.group(1) + "." + matcher.group(2).toLowerCase(Locale.ROOT)
            );
        }
    }
}
