package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TypeScript structural profile (fallback + Tree-sitter query).
 */
public final class TypeScriptLanguageProfile implements LanguageProfile {

    private static final Pattern IMPORT_FROM = Pattern.compile(
            "^\\s*import\\s+(?:type\\s+)?(?:[\\w*{}$\\s,]+\\s+from\\s+)?['\"]([^'\"]+)['\"]",
            Pattern.MULTILINE);
    private static final Pattern EXPORT_FROM = Pattern.compile(
            "^\\s*export\\s+(?:type\\s+)?(?:\\*|\\{[^}]*\\})\\s+from\\s+['\"]([^'\"]+)['\"]",
            Pattern.MULTILINE);
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern INTERFACE = Pattern.compile(
            "^\\s*(?:export\\s+)?interface\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*(?:export\\s+)?type\\s+(\\w+)\\s*=",
            Pattern.MULTILINE);
    private static final Pattern ENUM = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:const\\s+)?enum\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern FUNCTION = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\*?\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern CONST_FN = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:const|let)\\s+(\\w+)\\s*(?::\\s*[^=]+)?=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_]\\w*)\\s*=>",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (import_statement source: (_) @import)
            (export_statement source: (_) @import)
            (class_declaration name: (type_identifier) @class)
            (interface_declaration name: (type_identifier) @interface)
            (type_alias_declaration name: (type_identifier) @type)
            (enum_declaration name: (identifier) @enum)
            (function_declaration name: (identifier) @function)
            (lexical_declaration (variable_declarator name: (identifier) @function value: (arrow_function)))
            """;

    @Override
    public String id() {
        return "typescript";
    }

    @Override
    public String treeSitterLanguage() {
        return "TYPESCRIPT";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".ts", ".tsx");
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
        match(CLASS, source, "class", captures);
        match(INTERFACE, source, "interface", captures);
        match(TYPE_ALIAS, source, "type", captures);
        match(ENUM, source, "enum", captures);
        match(FUNCTION, source, "function", captures);
        match(CONST_FN, source, "function", captures);
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
