package io.repolens.parse.structural;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates Python framework endpoint and test fact extractors for a single file.
 * Does not emit CALLS.
 */
public final class PythonStructuralFactExtractor {

    private static final Pattern HTTP_TOKEN = Pattern.compile(
            "\\b(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_KEYWORD = Pattern.compile(
            "(?:^|,)\\s*path\\s*=\\s*");

    private PythonStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        if (!sink.endpointsFull()) {
            FlaskEndpointExtractor.extract(sink, path, source);
            FastApiEndpointExtractor.extract(sink, path, source);
        }
        PythonTestFactExtractor.extract(sink, path, source);
    }

    static void emitEndpoint(
            StructuralFactSink sink,
            String path,
            String source,
            int decoratorStart,
            int decoratorLength,
            String httpMethod,
            String route,
            String evidenceSummary
    ) {
        if (sink.endpointsFull() || httpMethod == null || route == null || route.isBlank()) {
            return;
        }
        int line = StructuralFactSink.lineOf(source, decoratorStart);
        int column = columnOf(source, decoratorStart);
        SourceLocation location = new SourceLocation(
                path, line, column, line, column + Math.max(decoratorLength, 1));
        Optional<Symbol> handler = resolveHandler(sink, path, line);
        Optional<String> ownerId = handler.flatMap(Symbol::parentSymbolId);
        Evidence evidence = new Evidence(
                InferenceMethod.ANNOTATION,
                Optional.of(location),
                Optional.empty(),
                Optional.of(evidenceSummary)
        );
        sink.addEndpoint(new Endpoint(
                "pending",
                httpMethod,
                route,
                ownerId,
                handler.map(Symbol::id),
                location,
                evidence
        ));
    }

    static Optional<Symbol> resolveHandler(StructuralFactSink sink, String path, int decoratorLine) {
        return sink.symbolsInFile(path).stream()
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION
                        || symbol.kind() == SymbolKind.METHOD)
                .filter(symbol -> symbol.location().startLine() >= decoratorLine)
                .min(Comparator
                        .comparingInt((Symbol symbol) -> symbol.location().startLine())
                        .thenComparing(Symbol::id));
    }

    static String parseDecoratorArgs(String source, int nameEnd) {
        int cursor = skipWhitespace(source, nameEnd);
        if (cursor >= source.length() || source.charAt(cursor) != '(') {
            return null;
        }
        int close = findMatchingParen(source, cursor);
        if (close < 0) {
            return null;
        }
        return source.substring(cursor + 1, close);
    }

    /**
     * Single static path, or null when missing/ambiguous.
     */
    static String extractSinglePath(String attrs) {
        if (attrs == null) {
            return null;
        }
        List<String> positional = positionalStringLiterals(attrs);
        Optional<String> named = namedPathLiteral(attrs);
        if (positional.size() > 1) {
            return null;
        }
        if (positional.size() == 1 && named.isPresent() && !positional.getFirst().equals(named.get())) {
            return null;
        }
        if (positional.size() == 1) {
            return positional.getFirst();
        }
        return named.orElse(null);
    }

    /**
     * Explicit {@code methods=[...]} values. Empty optional means the keyword was absent.
     * Empty list or more than one distinct method means ambiguous.
     */
    static Optional<List<String>> extractMethodsKeyword(String attrs) {
        int idx = indexOfUnquoted(attrs, "methods");
        if (idx < 0) {
            return Optional.empty();
        }
        int equals = skipWhitespace(attrs, idx + "methods".length());
        if (equals >= attrs.length() || attrs.charAt(equals) != '=') {
            return Optional.empty();
        }
        int valueStart = skipWhitespace(attrs, equals + 1);
        if (valueStart >= attrs.length()) {
            return Optional.of(List.of());
        }
        char open = attrs.charAt(valueStart);
        String body;
        if (open == '[' || open == '(') {
            int close = findMatching(attrs, valueStart, open, open == '[' ? ']' : ')');
            if (close < 0) {
                return Optional.of(List.of());
            }
            body = attrs.substring(valueStart + 1, close);
        } else {
            return Optional.of(List.of());
        }
        Set<String> methods = new LinkedHashSet<>();
        Matcher matcher = HTTP_TOKEN.matcher(body);
        while (matcher.find()) {
            methods.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return Optional.of(List.copyOf(methods));
    }

    static String unambiguousMethod(Optional<List<String>> methodsKeyword, String defaultWhenAbsent) {
        if (methodsKeyword.isEmpty()) {
            return defaultWhenAbsent;
        }
        List<String> methods = methodsKeyword.get();
        if (methods.size() != 1) {
            return null;
        }
        return methods.getFirst();
    }

    static int columnOf(String source, int offset) {
        int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        return offset - lineStart + 1;
    }

    static int skipWhitespace(String source, int index) {
        int i = index;
        while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
            i++;
        }
        return i;
    }

    static int findMatchingParen(String source, int openIdx) {
        return findMatching(source, openIdx, '(', ')');
    }

    private static List<String> positionalStringLiterals(String attrs) {
        List<String> values = new ArrayList<>();
        int i = 0;
        while (i < attrs.length()) {
            i = skipWhitespace(attrs, i);
            if (i >= attrs.length()) {
                break;
            }
            if (isKeywordStart(attrs, i)) {
                break;
            }
            LiteralRead literal = readStringLiteral(attrs, i);
            if (literal != null) {
                if (literal.interpolated()) {
                    return List.of();
                }
                values.add(literal.value());
                i = skipWhitespace(attrs, literal.end());
                if (i < attrs.length() && attrs.charAt(i) == ',') {
                    i++;
                }
                continue;
            }
            int nextComma = indexOfUnquotedChar(attrs, ',', i);
            if (nextComma < 0) {
                break;
            }
            i = nextComma + 1;
        }
        return values;
    }

    private static Optional<String> namedPathLiteral(String attrs) {
        Matcher matcher = PATH_KEYWORD.matcher(attrs);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int start = skipWhitespace(attrs, matcher.end());
        LiteralRead literal = readStringLiteral(attrs, start);
        if (literal == null || literal.interpolated()) {
            return Optional.empty();
        }
        return Optional.of(literal.value());
    }

    private static boolean isKeywordStart(String attrs, int index) {
        if (index >= attrs.length() || !Character.isJavaIdentifierStart(attrs.charAt(index))) {
            return false;
        }
        int i = index + 1;
        while (i < attrs.length() && Character.isJavaIdentifierPart(attrs.charAt(i))) {
            i++;
        }
        int equals = skipWhitespace(attrs, i);
        return equals < attrs.length() && attrs.charAt(equals) == '=';
    }

    private static LiteralRead readStringLiteral(String source, int index) {
        int i = index;
        boolean interpolated = false;
        if (i < source.length() && (source.charAt(i) == 'f' || source.charAt(i) == 'F'
                || source.charAt(i) == 'b' || source.charAt(i) == 'B')) {
            interpolated = source.charAt(i) == 'f' || source.charAt(i) == 'F';
            i++;
        }
        if (i < source.length() && (source.charAt(i) == 'r' || source.charAt(i) == 'R')) {
            i++;
        }
        if (i >= source.length()) {
            return null;
        }
        char quote = source.charAt(i);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        boolean triple = i + 2 < source.length()
                && source.charAt(i + 1) == quote
                && source.charAt(i + 2) == quote;
        int contentStart = i + (triple ? 3 : 1);
        int end = skipStringEnd(source, i, quote, triple);
        if (end < 0) {
            return null;
        }
        String raw = source.substring(contentStart, end - (triple ? 3 : 1));
        return new LiteralRead(raw, interpolated, end);
    }

    private static int skipStringEnd(String source, int quoteIdx, char quote, boolean triple) {
        int i = quoteIdx + (triple ? 3 : 1);
        while (i < source.length()) {
            if (!triple && source.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (triple) {
                if (i + 2 < source.length()
                        && source.charAt(i) == quote
                        && source.charAt(i + 1) == quote
                        && source.charAt(i + 2) == quote) {
                    return i + 3;
                }
            } else if (source.charAt(i) == quote) {
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    private static int indexOfUnquoted(String attrs, String token) {
        int i = 0;
        while (i < attrs.length()) {
            LiteralRead literal = readStringLiteral(attrs, i);
            if (literal != null) {
                i = literal.end();
                continue;
            }
            if (attrs.startsWith(token, i)
                    && (i == 0 || !Character.isJavaIdentifierPart(attrs.charAt(i - 1)))
                    && (i + token.length() >= attrs.length()
                    || !Character.isJavaIdentifierPart(attrs.charAt(i + token.length())))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int indexOfUnquotedChar(String attrs, char target, int from) {
        int i = from;
        while (i < attrs.length()) {
            LiteralRead literal = readStringLiteral(attrs, i);
            if (literal != null) {
                i = literal.end();
                continue;
            }
            if (attrs.charAt(i) == target) {
                return i;
            }
            i++;
        }
        return -1;
    }

    static int findMatching(String source, int openIdx, char open, char close) {
        int depth = 0;
        int i = openIdx;
        while (i < source.length()) {
            LiteralRead literal = readStringLiteral(source, i);
            if (literal != null) {
                i = literal.end();
                continue;
            }
            char c = source.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private record LiteralRead(String value, boolean interpolated, int end) {
    }
}
