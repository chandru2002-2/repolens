package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C# structural profile (fallback + Tree-sitter query).
 *
 * <p>Namespaces map to modules; {@code using} directives to imports;
 * structs/records map to {@code type}.
 */
public final class CSharpLanguageProfile implements LanguageProfile {

    private static final Pattern NAMESPACE = Pattern.compile(
            "^\\s*namespace\\s+([\\w.]+)\\s*[;{]", Pattern.MULTILINE);
    private static final Pattern USING = Pattern.compile(
            "^\\s*using\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:public\\s+|internal\\s+|private\\s+|protected\\s+)?"
                    + "(?:abstract\\s+|sealed\\s+|static\\s+|partial\\s+)*"
                    + "(class|interface|enum|struct|record)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+)(?:static\\s+|virtual\\s+|override\\s+|async\\s+)*"
                    + "(?:[\\w.<>\\[\\]]+)\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (namespace_declaration name: (_) @module)
            (file_scoped_namespace_declaration name: (_) @module)
            (using_directive (identifier) @import)
            (using_directive (qualified_name) @import)
            (class_declaration name: (identifier) @class)
            (interface_declaration name: (identifier) @interface)
            (enum_declaration name: (identifier) @enum)
            (struct_declaration name: (identifier) @type)
            (record_declaration name: (identifier) @type)
            (method_declaration name: (identifier) @method)
            """;

    @Override
    public String id() {
        return "csharp";
    }

    @Override
    public String treeSitterLanguage() {
        return "C_SHARP";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".cs");
    }

    @Override
    public String query() {
        return QUERY;
    }

    @Override
    public List<SyntaxCapture> fallbackExtract(String source) {
        List<SyntaxCapture> captures = new ArrayList<>();
        match(NAMESPACE, source, "module", captures);
        match(USING, source, "import", captures);

        Matcher types = TYPE.matcher(source);
        while (types.find()) {
            String kind = types.group(1);
            String name = types.group(2);
            String capture = switch (kind) {
                case "class" -> "class";
                case "interface" -> "interface";
                case "enum" -> "enum";
                default -> "type"; // struct, record
            };
            captures.add(SyntaxCapture.of(capture, name, lineOf(source, types.start())));
        }

        Matcher methods = METHOD.matcher(source);
        while (methods.find()) {
            String name = methods.group(1);
            if (Set.of("if", "for", "while", "switch", "catch", "using", "return", "new").contains(name)) {
                continue;
            }
            captures.add(SyntaxCapture.of("method", name, lineOf(source, methods.start())));
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
