package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript structural profile (fallback + Tree-sitter query).
 */
public final class JavaScriptLanguageProfile implements LanguageProfile {

    private static final Pattern IMPORT_FROM = Pattern.compile(
            "^\\s*import\\s+(?:type\\s+)?(?:[\\w*{}$\\s,]+\\s+from\\s+)?['\"]([^'\"]+)['\"]",
            Pattern.MULTILINE);
    private static final Pattern EXPORT_FROM = Pattern.compile(
            "^\\s*export\\s+(?:type\\s+)?(?:\\*|\\{[^}]*\\})\\s+from\\s+['\"]([^'\"]+)['\"]",
            Pattern.MULTILINE);
    private static final Pattern REQUIRE = Pattern.compile(
            "(?:(?:const|let|var)\\s+[\\w{}\\s,*=]+\\s*=\\s*)?require\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern FUNCTION = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\*?\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern CONST_FN = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_]\\w*)\\s*=>",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (import_statement source: (_) @import)
            (export_statement source: (_) @import)
            (call_expression function: (identifier) @reqFn arguments: (arguments (string) @import))
            (class_declaration name: (identifier) @class)
            (function_declaration name: (identifier) @function)
            (lexical_declaration (variable_declarator name: (identifier) @function value: (arrow_function)))
            """;

    @Override
    public String id() {
        return "javascript";
    }

    @Override
    public String treeSitterLanguage() {
        return "JAVASCRIPT";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".js", ".mjs", ".cjs", ".jsx");
    }

    @Override
    public String query() {
        return QUERY;
    }

    @Override
    public Optional<String> moduleFromCaptures(List<SyntaxCapture> captures, String relativePath) {
        return Optional.ofNullable(LanguageProfile.fileModuleName(relativePath));
    }

    @Override
    public List<SyntaxCapture> fallbackExtract(String source) {
        List<SyntaxCapture> captures = new ArrayList<>();
        match(IMPORT_FROM, source, "import", captures);
        match(EXPORT_FROM, source, "import", captures);
        match(REQUIRE, source, "import", captures);
        match(CLASS, source, "class", captures);
        match(FUNCTION, source, "function", captures);
        match(CONST_FN, source, "function", captures);
        return captures;
    }

    private static void match(Pattern pattern, String source, String name, List<SyntaxCapture> out) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String text = matcher.group(1);
            if (text == null || text.isBlank()) {
                continue;
            }
            if ("reqFn".equals(name)) {
                continue;
            }
            out.add(SyntaxCapture.of(name, stripQuotes(text), lineOf(source, matcher.start())));
        }
    }

    private static String stripQuotes(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
