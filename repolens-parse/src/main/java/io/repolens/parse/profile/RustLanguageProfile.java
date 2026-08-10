package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rust structural profile (fallback + Tree-sitter query).
 *
 * <p>Module identity uses file-path form (like JS/Python). Structs→{@code class},
 * traits→{@code interface}, enums→{@code enum}, type aliases→{@code type}.
 */
public final class RustLanguageProfile implements LanguageProfile {

    private static final Pattern USE = Pattern.compile(
            "^\\s*(?:pub\\s+)?use\\s+((?:crate|super|self)?(?:::)?[\\w:]+)(?:\\s*::\\s*\\{[^}]*\\})?\\s*;",
            Pattern.MULTILINE);
    private static final Pattern MOD = Pattern.compile(
            "^\\s*(?:pub\\s+)?mod\\s+(\\w+)\\s*[;{]", Pattern.MULTILINE);
    private static final Pattern STRUCT = Pattern.compile(
            "^\\s*(?:pub\\s+)?struct\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern ENUM = Pattern.compile(
            "^\\s*(?:pub\\s+)?enum\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern TRAIT = Pattern.compile(
            "^\\s*(?:pub\\s+)?trait\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*(?:pub\\s+)?type\\s+(\\w+)\\s*=", Pattern.MULTILINE);
    private static final Pattern TOP_FN = Pattern.compile(
            "^\\s*(?:pub\\s+)?(?:async\\s+)?(?:unsafe\\s+)?(?:const\\s+)?fn\\s+(\\w+)\\s*[<(]",
            Pattern.MULTILINE);
    private static final Pattern IMPL_FN = Pattern.compile(
            "^[ \\t]+(?:pub\\s+)?(?:async\\s+)?(?:unsafe\\s+)?(?:const\\s+)?fn\\s+(\\w+)\\s*[<(]",
            Pattern.MULTILINE);

    private static final String QUERY = """
            (use_declaration argument: (_) @import)
            (mod_item name: (identifier) @module)
            (struct_item name: (type_identifier) @class)
            (enum_item name: (type_identifier) @enum)
            (trait_item name: (type_identifier) @interface)
            (type_item name: (type_identifier) @type)
            (function_item name: (identifier) @function)
            (impl_item body: (declaration_list (function_item name: (identifier) @method)))
            """;

    @Override
    public String id() {
        return "rust";
    }

    @Override
    public String treeSitterLanguage() {
        return "RUST";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".rs");
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

        Matcher uses = USE.matcher(source);
        while (uses.find()) {
            String path = normalizeUsePath(uses.group(1));
            if (!path.isBlank()) {
                captures.add(SyntaxCapture.of("import", path, lineOf(source, uses.start())));
            }
        }

        match(MOD, source, "module", captures);
        match(STRUCT, source, "class", captures);
        match(ENUM, source, "enum", captures);
        match(TRAIT, source, "interface", captures);
        match(TYPE_ALIAS, source, "type", captures);
        match(TOP_FN, source, "function", captures);
        match(IMPL_FN, source, "method", captures);
        return captures;
    }

    /**
     * Reduce {@code crate::util::Helper} / {@code util::Helper} to a resolvable module key
     * ({@code util} or {@code crate::util}'s last meaningful segment for local crates).
     */
    static String normalizeUsePath(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("crate::")) {
            value = value.substring("crate::".length());
        } else if (value.startsWith("super::")) {
            value = value.substring("super::".length());
        } else if (value.startsWith("self::")) {
            value = value.substring("self::".length());
        }
        int colon = value.indexOf("::");
        if (colon > 0) {
            value = value.substring(0, colon);
        }
        return value.replace("::", "/");
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
