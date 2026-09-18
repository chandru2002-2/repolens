package io.repolens.parse.structural;

import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PythonTestFactExtractor {

    private static final Pattern PARAMETRIZE =
            Pattern.compile("@pytest\\.mark\\.parametrize\\b");

    private static final Pattern UNITTEST_CLASS =
            Pattern.compile(
                    "class\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\([^\\n)]*(?:TestCase|unittest\\.TestCase)[^\\n)]*\\)");

    private static final Set<String> TEST_FILE_NAMES = Set.of(
            "conftest.py"
    );

    private PythonTestFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        if (sink.testsFull() || source == null || source.isBlank()) {
            return;
        }

        boolean testFile = isTestFile(path);
        boolean testPath = isTestPath(path) && !isConftestFile(path);

        emitParametrizedTests(sink, path, source);

        if (sink.testsFull()) {
            return;
        }

        emitUnittestMethods(sink, path, source);

        if (sink.testsFull()) {
            return;
        }

        emitNamedTests(sink, path, testFile, testPath);
    }

    private static void emitParametrizedTests(
            StructuralFactSink sink,
            String path,
            String source
    ) {
        Matcher matcher = PARAMETRIZE.matcher(source);

        while (matcher.find() && !sink.testsFull()) {
            int line = StructuralFactSink.lineOf(source, matcher.start());

            Optional<Symbol> symbol = nearestTestableSymbol(sink, path, line);
            if (symbol.isEmpty()) {
                continue;
            }

            addDiscovered(
                    sink,
                    symbol.get(),
                    "pytest",
                    locationAt(path, source, matcher.start()),
                    InferenceMethod.ANNOTATION,
                    "@pytest.mark.parametrize"
            );
        }
    }

    private static void emitUnittestMethods(
            StructuralFactSink sink,
            String path,
            String source
    ) {
        if (!UNITTEST_CLASS.matcher(source).find()) {
            return;
        }

        for (Symbol symbol : sink.symbolsInFile(path)) {
            if (sink.testsFull()) {
                return;
            }

            if (!isTestableSymbol(symbol) || !symbol.name().startsWith("test_")) {
                continue;
            }

            addDiscovered(
                    sink,
                    symbol,
                    "unittest",
                    symbol.location(),
                    InferenceMethod.FRAMEWORK_CONVENTION,
                    "unittest TestCase test method"
            );
        }
    }

    private static void emitNamedTests(
            StructuralFactSink sink,
            String path,
            boolean testFile,
            boolean testPath
    ) {
        if (!testFile && !testPath) {
            return;
        }

        for (Symbol symbol : sink.symbolsInFile(path)) {
            if (sink.testsFull()) {
                return;
            }

            if (!isTestableSymbol(symbol)
                    || !symbol.name().startsWith("test_")
                    || sink.hasTestForSymbol(symbol.id())) {
                continue;
            }

            addDiscovered(
                    sink,
                    symbol,
                    "pytest",
                    symbol.location(),
                    InferenceMethod.NAME_HEURISTIC,
                    "test-function " + symbol.name()
            );
        }
    }

    private static Optional<Symbol> nearestTestableSymbol(
            StructuralFactSink sink,
            String path,
            int line
    ) {
        return sink.symbolsInFile(path).stream()
                .filter(PythonTestFactExtractor::isTestableSymbol)
                .filter(symbol -> symbol.location().startLine() >= line)
                .min(Comparator
                        .comparingInt((Symbol symbol) -> symbol.location().startLine())
                        .thenComparing(Symbol::id));
    }

    private static boolean isTestableSymbol(Symbol symbol) {
        return symbol.kind() == SymbolKind.FUNCTION
                || symbol.kind() == SymbolKind.METHOD;
    }

    private static boolean isTestFile(String path) {
        String file = fileName(path).toLowerCase(Locale.ROOT);

        if (TEST_FILE_NAMES.contains(file)) {
            return false;
        }

        return file.startsWith("test_")
                || file.endsWith("_test.py");
    }

    private static boolean isConftestFile(String path) {
        return fileName(path).equalsIgnoreCase("conftest.py");
    }

    private static boolean isTestPath(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);

        String[] parts = normalized.split("/");
        for (String part : parts) {
            if (part.equals("test") || part.equals("tests")) {
                return true;
            }
        }

        return false;
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

    private static SourceLocation locationAt(
            String path,
            String source,
            int offset
    ) {
        int line = StructuralFactSink.lineOf(source, offset);
        int lineStart =
                source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int column = offset - lineStart + 1;

        return new SourceLocation(
                path,
                line,
                column,
                line,
                column + 1
        );
    }

    private static String fileName(String path) {
        int slash = Math.max(
                path.lastIndexOf('/'),
                path.lastIndexOf('\\')
        );

        return slash >= 0
                ? path.substring(slash + 1)
                : path;
    }
}
