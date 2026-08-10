package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kotlin structural profile (fallback + Tree-sitter query).
 *
 * <p>Packages map to modules; objects map to {@code class}; top-level {@code fun} to
 * {@code function}; indented members to {@code method}.
 */
public final class KotlinLanguageProfile implements LanguageProfile {

    private static final Pattern PACKAGE = Pattern.compile(
            "^\\s*package\\s+([\\w.]+)\\s*", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+([\\w.]+)(?:\\s*\\.\\s*\\*)?", Pattern.MULTILINE);
    private static final Pattern ENUM_CLASS = Pattern.compile(
            "^\\s*(?:(?:public|private|internal|protected)\\s+)*enum\\s+class\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:(?:public|private|internal|protected|abstract|open|sealed|data|annotation|inner)\\s+)*"
                    + "(class|interface|object)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern TOP_FUN = Pattern.compile(
            "^(?:(?:public|private|internal|protected|suspend|inline|tailrec)\\s+)*fun\\s+(?:<[^>]+>\\s+)?(\\w+)\\s*[<(]",
            Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^[ \\t]+(?:(?:public|private|internal|protected|override|suspend|inline|open|abstract)\\s+)*"
                    + "fun\\s+(?:<[^>]+>\\s+)?(\\w+)\\s*[<(]",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (package_header (identifier) @module)
            (package_header (qualified_identifier) @module)
            (import (identifier) @import)
            (import (qualified_identifier) @import)
            (class_declaration (type_identifier) @class)
            (object_declaration (type_identifier) @class)
            (interface_declaration (type_identifier) @interface)
            (function_declaration (simple_identifier) @function)
            """;

    @Override
    public String id() {
        return "kotlin";
    }

    @Override
    public String treeSitterLanguage() {
        return "KOTLIN";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".kt", ".kts");
    }

    @Override
    public String query() {
        return QUERY;
    }

    @Override
    public List<SyntaxCapture> fallbackExtract(String source) {
        List<SyntaxCapture> captures = new ArrayList<>();
        match(PACKAGE, source, "module", captures);
        match(IMPORT, source, "import", captures);

        match(ENUM_CLASS, source, "enum", captures);

        Matcher types = TYPE.matcher(source);
        while (types.find()) {
            String kind = types.group(1);
            String name = types.group(2);
            boolean alreadyEnum = captures.stream()
                    .anyMatch(c -> "enum".equals(c.name()) && c.text().equals(name));
            if (alreadyEnum) {
                continue;
            }
            String capture = "interface".equals(kind) ? "interface" : "class";
            captures.add(SyntaxCapture.of(capture, name, lineOf(source, types.start())));
        }

        match(TOP_FUN, source, "function", captures);
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
