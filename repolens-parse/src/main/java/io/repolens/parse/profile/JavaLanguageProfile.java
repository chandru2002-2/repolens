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
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+)?(class|interface|enum)\\s+(\\w+)"
                    + "(?:\\s+extends\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*))?"
                    + "(?:\\s+implements\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*))?",
            Pattern.MULTILINE);
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)(?:static\\s+)?(?:final\\s+|default\\s+|synchronized\\s+)*(?:<[^>]+>\\s+)?[\\w.]+(?:\\s*<[^>]+>)?(?:\\[\\])*\\s+([a-z]\\w*)\\s*\\(",
            Pattern.MULTILINE);
    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?(?:final\\s+)?(?:volatile\\s+|transient\\s+)?"
                    + "[\\w.]+(?:\\s*<[^>]+>)?(?:\\[\\])*\\s+([a-zA-Z_]\\w*)\\s*(?:=|;)",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (package_declaration (scoped_identifier) @module)
            (package_declaration (identifier) @module)
            (import_declaration) @import
            (class_declaration name: (identifier) @class)
            (interface_declaration name: (identifier) @interface)
            (enum_declaration name: (identifier) @enum)
            (method_declaration name: (identifier) @method)
            (field_declaration (variable_declarator name: (identifier) @field))
            (superclass (type_identifier) @extends)
            (super_interfaces (type_list (type_identifier) @implements))
            (extends_interfaces (type_list (type_identifier) @extends))
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
            if ("enum".equals(kind)) {
                continue;
            }
            String extendsClause = types.group(3);
            if (extendsClause != null && !extendsClause.isBlank()) {
                for (String typeName : extendsClause.split("\\s*,\\s*")) {
                    String simple = simpleName(typeName);
                    if (!simple.isBlank()) {
                        // Interfaces use extends for other interfaces; classes use extends for superclass.
                        String capture = "interface".equals(kind) ? "extends" : "extends";
                        captures.add(SyntaxCapture.of(capture, simple, line));
                    }
                }
            }
            String implementsClause = types.group(4);
            if (implementsClause != null && !implementsClause.isBlank()) {
                for (String typeName : implementsClause.split("\\s*,\\s*")) {
                    String simple = simpleName(typeName);
                    if (!simple.isBlank()) {
                        captures.add(SyntaxCapture.of("implements", simple, line));
                    }
                }
            }
        }
        Matcher methods = METHOD.matcher(source);
        while (methods.find()) {
            String name = methods.group(1);
            if (Set.of("if", "for", "while", "switch", "catch", "return").contains(name)) {
                continue;
            }
            captures.add(SyntaxCapture.of("method", name, lineOf(source, methods.start())));
        }
        Matcher fields = FIELD.matcher(source);
        while (fields.find()) {
            String name = fields.group(1);
            if (Set.of("class", "interface", "enum", "return", "new", "throw").contains(name)) {
                continue;
            }
            // Skip method-like matches already captured.
            if (Character.isLowerCase(name.charAt(0)) && source.contains(name + "(")) {
                // still allow fields; methods are captured separately
            }
            captures.add(SyntaxCapture.of("field", name, lineOf(source, fields.start())));
        }
        return captures;
    }

    private static String simpleName(String typeName) {
        String value = typeName.trim();
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
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
