package io.repolens.parse.structural;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flask {@code @app.route} / {@code @blueprint.route} endpoint facts.
 * Does not apply Blueprint {@code url_prefix} from other files.
 */
public final class FlaskEndpointExtractor {

    private static final Pattern ROUTE = Pattern.compile("@(\\w+)\\.route\\b");

    private FlaskEndpointExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        Matcher matcher = ROUTE.matcher(source);
        while (matcher.find() && !sink.endpointsFull()) {
            String attrs = PythonStructuralFactExtractor.parseDecoratorArgs(source, matcher.end());
            if (attrs == null) {
                continue;
            }
            String route = PythonStructuralFactExtractor.extractSinglePath(attrs);
            if (route == null || route.isBlank()) {
                continue;
            }
            String httpMethod = PythonStructuralFactExtractor.unambiguousMethod(
                    PythonStructuralFactExtractor.extractMethodsKeyword(attrs),
                    "GET");
            if (httpMethod == null) {
                continue;
            }
            PythonStructuralFactExtractor.emitEndpoint(
                    sink,
                    path,
                    source,
                    matcher.start(),
                    matcher.end() - matcher.start(),
                    httpMethod,
                    route,
                    "@" + matcher.group(1) + ".route"
            );
        }
    }
}
