package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaLanguageProfile implements LanguageProfile {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+)?(class|interface|enum)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)(?:static\\s+)?(?:final\\s+|default\\s+|synchronized\\s+)*(?:<[^>]+>\\s+)?[\\w.]+(?:\\s*<[^>]+>)?(?:\\[\\])*\\s+([a-z]\\w*)\\s*\\(",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (package_declaration (scoped_identifier) @module)
            (package_declaration (identifier) @module)
            (import_declaration) @import
            (class_declaration name: (identifier) @class)
            (interface_declaration name: (identifier) @interface)
            (enum_declaration name: (identifier) @enum)
            (method_declaration name: (identifier) @method)
            """;

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String treeSitterLanguage() {
        return "JAVA";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".java");
    }

    @Override
    public String query() {
        return QUERY;
    }

    @Override
    public List<SyntaxCapture> fallbackExtract(String source) {
        List<SyntaxCapture> captures = new ArrayList<>();
        matchAll(PACKAGE, source, "module", captures);
        matchAll(IMPORT, source, "import", captures);
        Matcher types = TYPE.matcher(source);
        while (types.find()) {
            String kind = types.group(1);
            String name = types.group(2);
            int line = lineOf(source, types.start());
            captures.add(SyntaxCapture.of(kind, name, line));
        }
        Matcher methods = METHOD.matcher(source);
        while (methods.find()) {
            String name = methods.group(1);
            if (Set.of("if", "for", "while", "switch", "catch", "return").contains(name)) {
                continue;
            }
            captures.add(SyntaxCapture.of("method", name, lineOf(source, methods.start())));
        }
        return captures;
    }

    private static void matchAll(Pattern pattern, String source, String captureName, List<SyntaxCapture> out) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String text = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            out.add(SyntaxCapture.of(captureName, text.trim(), lineOf(source, matcher.start())));
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
