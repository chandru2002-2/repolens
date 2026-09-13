package io.repolens.parse.structural;

import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers Java test types and methods as first-class Test facts (ADR-012).
 * Does not infer subjects or TESTS relationships.
 */
public final class JavaTestFactExtractor {

    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test(?![A-Za-z0-9_])");
    private static final Pattern PARAMETERIZED = Pattern.compile("@ParameterizedTest\\b");
    private static final Pattern NESTED = Pattern.compile("@Nested\\b");
    private static final Pattern JUNIT5_IMPORT = Pattern.compile("import\\s+org\\.junit\\.jupiter\\b");
    private static final Pattern JUNIT4_TEST_IMPORT = Pattern.compile(
            "import\\s+org\\.junit\\.Test\\b|import\\s+org\\.junit\\.\\*;");

    private JavaTestFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        if (sink.testsFull() || source == null || source.isBlank()) {
            return;
        }
        boolean junit5 = JUNIT5_IMPORT.matcher(source).find();
        boolean junit4 = JUNIT4_TEST_IMPORT.matcher(source).find();

        emitAnnotationTests(sink, path, source, TEST_ANNOTATION, "Test", frameworkHint(junit5, junit4));
        emitAnnotationTests(sink, path, source, PARAMETERIZED, "ParameterizedTest", "junit5");
        emitNestedTypes(sink, path, source);

        emitNameAndFileFallbacks(sink, path);
    }

    private static void emitAnnotationTests(
            StructuralFactSink sink,
            String path,
            String source,
            Pattern pattern,
            String annotationName,
            String frameworkHint
    ) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find() && !sink.testsFull()) {
            int line = StructuralFactSink.lineOf(source, matcher.start());
            Optional<Symbol> method = nearestMethod(sink, path, line);
            if (method.isEmpty()) {
                continue;
            }
            addDiscovered(sink, method.get(), frameworkHint, locationAt(path, source, matcher.start()),
                    InferenceMethod.ANNOTATION, "@" + annotationName);
        }
    }

    private static void emitNestedTypes(StructuralFactSink sink, String path, String source) {
        Matcher matcher = NESTED.matcher(source);
        while (matcher.find() && !sink.testsFull()) {
            int line = StructuralFactSink.lineOf(source, matcher.start());
            Optional<Symbol> type = nearestType(sink, path, line);
            if (type.isEmpty()) {
                continue;
            }
            addDiscovered(sink, type.get(), "junit5", locationAt(path, source, matcher.start()),
                    InferenceMethod.ANNOTATION, "@Nested");
        }
    }

    private static void emitNameAndFileFallbacks(StructuralFactSink sink, String path) {
        String fileName = fileName(path);
        boolean fileLooksLikeTest = looksLikeTestName(stripExtension(fileName));
        Symbol primary = sink.typeByFile().get(path);
        for (Symbol symbol : sink.symbolsInFile(path)) {
            if (sink.testsFull()) {
                return;
            }
            if (!isType(symbol.kind()) || sink.hasTestForSymbol(symbol.id())) {
                continue;
            }
            boolean nameMatch = looksLikeTestName(symbol.name());
            boolean fileMatch = fileLooksLikeTest && primary != null && symbol.id().equals(primary.id());
            if (!nameMatch && !fileMatch) {
                continue;
            }
            String summary = nameMatch ? "type-name " + symbol.name() : "file-name " + fileName;
            addDiscovered(
                    sink,
                    symbol,
                    null,
                    symbol.location(),
                    InferenceMethod.NAME_HEURISTIC,
                    summary
            );
        }
    }

    private static void addDiscovered(
            StructuralFactSink sink,
            Symbol symbol,
            String frameworkHint,
            SourceLocation location,
            InferenceMethod method,
            String summary
    ) {
        Evidence evidence = new Evidence(
                method,
                Optional.of(location),
                Optional.empty(),
                Optional.of(summary)
        );
        sink.addTest(new Test(
                "pending",
                symbol.id(),
                Optional.ofNullable(frameworkHint),
                location,
                evidence
        ));
    }

    private static Optional<Symbol> nearestMethod(StructuralFactSink sink, String path, int annotationLine) {
        return sink.symbolsInFile(path).stream()
                .filter(symbol -> symbol.kind() == SymbolKind.METHOD)
                .filter(symbol -> symbol.location().startLine() >= annotationLine)
                .min(Comparator.comparingInt(symbol -> symbol.location().startLine()));
    }

    private static Optional<Symbol> nearestType(StructuralFactSink sink, String path, int annotationLine) {
        return sink.symbolsInFile(path).stream()
                .filter(symbol -> isType(symbol.kind()))
                .filter(symbol -> symbol.location().startLine() >= annotationLine)
                .min(Comparator.comparingInt(symbol -> symbol.location().startLine()));
    }

    static boolean looksLikeTestName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.endsWith("Tests") || name.endsWith("Test")) {
            return true;
        }
        return name.endsWith("IT") && name.length() > 2
                && Character.isLowerCase(name.charAt(name.length() - 3));
    }

    private static String frameworkHint(boolean junit5, boolean junit4) {
        if (junit5) {
            return "junit5";
        }
        if (junit4) {
            return "junit4";
        }
        return "junit";
    }

    private static SourceLocation locationAt(String path, String source, int offset) {
        int line = StructuralFactSink.lineOf(source, offset);
        int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int column = offset - lineStart + 1;
        return new SourceLocation(path, line, column, line, column);
    }

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE;
    }

    private static String fileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
