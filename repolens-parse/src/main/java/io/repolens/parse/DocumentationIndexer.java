package io.repolens.parse;

import io.repolens.core.model.DocumentationDocument;
import io.repolens.core.model.DocumentationReference;
import io.repolens.core.model.DocumentationSection;
import io.repolens.core.model.Module;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic README / docs/ markdown indexer. Associates documentation with
 * repository entities only when an explicit textual match is found.
 */
public final class DocumentationIndexer {

    private static final int MAX_DOCS = 40;
    private static final int MAX_SECTION_CHARS = 1_200;
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern FQN = Pattern.compile("\\b([a-z][\\w]*(\\.[A-Za-z_][\\w]*)+)\\b");
    private static final Pattern BACKTICK = Pattern.compile("`([^`\\n]{2,120})`");
    private static final Pattern CODE_SPAN_NAME = Pattern.compile("\\b([A-Z][A-Za-z0-9_]{1,80})\\b");

    private DocumentationIndexer() {
    }

    public static void index(
            RepositoryModel.Builder builder,
            Path workingTree,
            WorkingTreeInventory inventory,
            List<Symbol> symbols,
            List<Module> modules
    ) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(workingTree, "workingTree");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(modules, "modules");

        List<String> docPaths = inventory.files().stream()
                .map(WorkingTreeInventory.InventoriedFile::relativePath)
                .filter(DocumentationIndexer::isDocumentationPath)
                .sorted(Comparator.comparing(DocumentationIndexer::docPriority)
                        .thenComparing(String::compareTo))
                .limit(MAX_DOCS)
                .toList();

        Map<String, String> matchTargets = buildMatchTargets(symbols, modules);
        int docSeq = 0;
        int refSeq = 0;
        Set<String> seenRefs = new LinkedHashSet<>();

        for (String relativePath : docPaths) {
            Path absolute = workingTree.resolve(relativePath);
            String markdown = readMarkdown(absolute);
            if (markdown == null || markdown.isBlank()) {
                continue;
            }

            String docId = "doc:" + (++docSeq);
            List<DocumentationSection> sections = splitSections(docId, markdown);
            String title = sections.isEmpty()
                    ? Path.of(relativePath).getFileName().toString()
                    : firstNonBlank(sections.getFirst().heading(), Path.of(relativePath).getFileName().toString());
            builder.addDocumentation(new DocumentationDocument(docId, relativePath, title, sections));

            for (DocumentationSection section : sections) {
                for (Map.Entry<String, String> entry : matchTargets.entrySet()) {
                    String needle = entry.getKey();
                    String entityId = entry.getValue();
                    if (!sectionMentions(section, needle)) {
                        continue;
                    }
                    String refKey = docId + "|" + section.id() + "|" + entityId + "|" + needle;
                    if (!seenRefs.add(refKey)) {
                        continue;
                    }
                    builder.addDocumentationReference(new DocumentationReference(
                            "docref:" + (++refSeq),
                            docId,
                            section.id(),
                            entityId,
                            needle
                    ));
                }
            }
        }
    }

    static boolean isDocumentationPath(String relativePath) {
        String path = relativePath.replace('\\', '/');
        String lower = path.toLowerCase(Locale.ROOT);
        String fileName = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.startsWith("readme") && (fileName.endsWith(".md") || fileName.equals("readme"))) {
            return true;
        }
        return lower.startsWith("docs/") && lower.endsWith(".md");
    }

    private static int docPriority(String relativePath) {
        String lower = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (lower.equals("readme.md") || lower.equals("readme")) {
            return 0;
        }
        if (Path.of(lower).getFileName().toString().startsWith("readme")) {
            return 1;
        }
        return 2;
    }

    static List<DocumentationSection> splitSections(String docId, String markdown) {
        List<DocumentationSection> sections = new ArrayList<>();
        String[] lines = markdown.split("\\R", -1);
        String currentHeading = "Introduction";
        int currentStart = 1;
        StringBuilder body = new StringBuilder();
        int sectionSeq = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                flushSection(sections, docId, ++sectionSeq, currentHeading, body, currentStart);
                currentHeading = heading.group(2).trim();
                currentStart = i + 1;
                body.setLength(0);
                continue;
            }
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(line);
        }
        flushSection(sections, docId, ++sectionSeq, currentHeading, body, currentStart);
        return sections;
    }

    private static void flushSection(
            List<DocumentationSection> sections,
            String docId,
            int sectionSeq,
            String heading,
            StringBuilder body,
            int startLine
    ) {
        String text = truncate(body.toString().trim());
        if (text.isBlank() && (heading == null || heading.isBlank())) {
            return;
        }
        sections.add(new DocumentationSection(
                docId + ":section:" + sectionSeq,
                heading == null || heading.isBlank() ? "Introduction" : heading,
                text,
                startLine
        ));
    }

    private static Map<String, String> buildMatchTargets(List<Symbol> symbols, List<Module> modules) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (Module module : modules) {
            putUnique(targets, module.name(), module.id());
        }
        for (Symbol symbol : symbols) {
            if (!isMatchableSymbol(symbol.kind())) {
                continue;
            }
            putUnique(targets, symbol.name(), symbol.id());
            symbol.moduleId().ifPresent(moduleId -> {
                // Prefer module name from modules list for FQN construction via later lookup.
            });
            String fqn = symbol.moduleId()
                    .flatMap(mid -> modules.stream().filter(m -> m.id().equals(mid)).findFirst())
                    .map(module -> module.name() + "." + symbol.name())
                    .orElse(null);
            if (fqn != null) {
                putUnique(targets, fqn, symbol.id());
            }
        }
        // Longer keys first helps sectionMentions prefer FQN when both match, via iteration order
        // for refs; matching itself is independent per needle.
        return targets;
    }

    private static boolean isMatchableSymbol(SymbolKind kind) {
        return kind == SymbolKind.CLASS
                || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM
                || kind == SymbolKind.TYPE
                || kind == SymbolKind.FUNCTION;
    }

    private static void putUnique(Map<String, String> targets, String needle, String entityId) {
        if (needle == null || needle.isBlank()) {
            return;
        }
        // Ambiguous simple names: keep the first entity only (deterministic).
        targets.putIfAbsent(needle, entityId);
    }

    static boolean sectionMentions(DocumentationSection section, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String haystack = section.heading() + "\n" + section.text();
        if (needle.indexOf('.') >= 0) {
            return containsFqn(haystack, needle) || containsBacktick(haystack, needle);
        }
        return containsBacktick(haystack, needle) || containsWord(haystack, needle);
    }

    private static boolean containsBacktick(String haystack, String needle) {
        Matcher matcher = BACKTICK.matcher(haystack);
        while (matcher.find()) {
            if (matcher.group(1).equals(needle) || matcher.group(1).endsWith("." + needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFqn(String haystack, String needle) {
        Matcher matcher = FQN.matcher(haystack);
        while (matcher.find()) {
            if (matcher.group(1).equals(needle)) {
                return true;
            }
        }
        Matcher tick = BACKTICK.matcher(haystack);
        while (tick.find()) {
            if (tick.group(1).equals(needle)) {
                return true;
            }
        }
        return haystack.contains(needle);
    }

    private static boolean containsWord(String haystack, String needle) {
        // Prefer explicit code spans / PascalCase tokens to reduce false positives.
        Matcher tick = BACKTICK.matcher(haystack);
        while (tick.find()) {
            String value = tick.group(1);
            if (value.equals(needle) || value.endsWith("." + needle)) {
                return true;
            }
        }
        if (Character.isUpperCase(needle.charAt(0))) {
            Matcher names = CODE_SPAN_NAME.matcher(haystack);
            while (names.find()) {
                if (names.group(1).equals(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String readMarkdown(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_SECTION_CHARS) {
            return text;
        }
        return text.substring(0, MAX_SECTION_CHARS).trim() + "…";
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
