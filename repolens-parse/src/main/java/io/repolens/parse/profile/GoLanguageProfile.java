package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Go structural profile (fallback + Tree-sitter query).
 *
 * <p>Module identity uses the containing directory path (Go: one package per directory)
 * so import paths like {@code "demo/util"} resolve via DependencyAnalyzer.
 * Structs map to {@code class}, interfaces to {@code interface}.
 */
public final class GoLanguageProfile implements LanguageProfile {

    private static final Pattern PACKAGE = Pattern.compile(
            "^\\s*package\\s+(\\w+)\\s*", Pattern.MULTILINE);
    private static final Pattern IMPORT_SINGLE = Pattern.compile(
            "^\\s*import\\s+(?:\\w+\\s+)?\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern IMPORT_GROUPED = Pattern.compile(
            "\"([^\"]+)\"");
    private static final Pattern IMPORT_BLOCK = Pattern.compile(
            "import\\s*\\((.*?)\\)", Pattern.DOTALL);
    private static final Pattern TYPE_STRUCT = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s+struct\\b", Pattern.MULTILINE);
    private static final Pattern TYPE_INTERFACE = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s+interface\\b", Pattern.MULTILINE);
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s*=", Pattern.MULTILINE);
    private static final Pattern TYPE_DEF = Pattern.compile(
            "^\\s*type\\s+(\\w+)\\s+[A-Za-z_][\\w.]*\\s*$", Pattern.MULTILINE);
    private static final Pattern FUNC = Pattern.compile(
            "^\\s*func\\s+(?:\\([^)]*\\)\\s+)?(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*func\\s+\\([^)]+\\)\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);

    private static final String QUERY = """
            (package_clause (package_identifier) @module)
            (import_spec path: (_) @import)
            (type_declaration (type_spec name: (type_identifier) @class type: (struct_type)))
            (type_declaration (type_spec name: (type_identifier) @interface type: (interface_type)))
            (type_declaration (type_spec name: (type_identifier) @type type: (type_identifier)))
            (function_declaration name: (identifier) @function)
            (method_declaration name: (field_identifier) @method)
            """;

    @Override
    public String id() {
        return "go";
    }

    @Override
    public String treeSitterLanguage() {
        return "GO";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".go");
    }

    @Override
    public String query() {
        return QUERY;
    }

    @Override
    public Optional<String> moduleFromCaptures(List<SyntaxCapture> captures, String relativePath) {
        String path = relativePath.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        if (slash > 0) {
            return Optional.of(path.substring(0, slash));
        }
        return captures.stream()
                .filter(c -> "module".equals(c.name()))
                .map(SyntaxCapture::text)
                .findFirst();
    }

    @Override
    public List<SyntaxCapture> fallbackExtract(String source) {
        List<SyntaxCapture> captures = new ArrayList<>();
        match(PACKAGE, source, "module", captures);

        match(IMPORT_SINGLE, source, "import", captures);
        Matcher blocks = IMPORT_BLOCK.matcher(source);
        while (blocks.find()) {
            Matcher paths = IMPORT_GROUPED.matcher(blocks.group(1));
            while (paths.find()) {
                captures.add(SyntaxCapture.of("import", paths.group(1), lineOf(source, blocks.start() + paths.start())));
            }
        }

        match(TYPE_STRUCT, source, "class", captures);
        match(TYPE_INTERFACE, source, "interface", captures);

        for (Pattern typePat : List.of(TYPE_ALIAS, TYPE_DEF)) {
            Matcher aliases = typePat.matcher(source);
            while (aliases.find()) {
                String name = aliases.group(1);
                boolean already = captures.stream().anyMatch(c ->
                        Set.of("class", "interface", "type").contains(c.name()) && c.text().equals(name));
                if (!already) {
                    captures.add(SyntaxCapture.of("type", name, lineOf(source, aliases.start())));
                }
            }
        }

        Matcher methods = METHOD.matcher(source);
        while (methods.find()) {
            captures.add(SyntaxCapture.of("method", methods.group(1), lineOf(source, methods.start())));
        }
        Matcher funcs = FUNC.matcher(source);
        while (funcs.find()) {
            String name = funcs.group(1);
            boolean isMethod = captures.stream()
                    .anyMatch(c -> "method".equals(c.name()) && c.text().equals(name)
                            && c.startLine() == lineOf(source, funcs.start()));
            if (!isMethod) {
                captures.add(SyntaxCapture.of("function", name, lineOf(source, funcs.start())));
            }
        }
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
