package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python structural profile (fallback + Tree-sitter query).
 */
public final class PythonLanguageProfile implements LanguageProfile {

    private static final Pattern FROM_IMPORT = Pattern.compile(
            "^\\s*from\\s+([.\\w]+)\\s+import\\s+([\\w\\s,*]+)",
            Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*)",
            Pattern.MULTILINE);
    private static final Pattern CLASS = Pattern.compile("^class\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern TOP_LEVEL_FUNCTION = Pattern.compile(
            "^(?:async\\s+)?def\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^[ \\t]+(?:async\\s+)?def\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (import_statement name: (_) @import)
            (import_from_statement module_name: (_) @import)
            (class_definition name: (identifier) @class)
            (function_definition name: (identifier) @function)
            """;

    @Override
    public String id() {
        return "python";
    }

    @Override
    public String treeSitterLanguage() {
        return "PYTHON";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".py");
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

        Matcher fromImports = FROM_IMPORT.matcher(source);
        while (fromImports.find()) {
            String module = fromImports.group(1).trim();
            captures.add(SyntaxCapture.of("import", module, lineOf(source, fromImports.start())));
        }

        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            String[] parts = imports.group(1).split(",");
            for (String part : parts) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    captures.add(SyntaxCapture.of("import", name, lineOf(source, imports.start())));
                }
            }
        }

        match(CLASS, source, "class", captures);
        match(TOP_LEVEL_FUNCTION, source, "function", captures);
        match(METHOD, source, "method", captures);
        return captures;
    }

    private static void match(Pattern pattern, String source, String name, List<SyntaxCapture> out) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            out.add(SyntaxCapture.of(name, matcher.group(1), lineOf(source, matcher.start())));
        }
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
