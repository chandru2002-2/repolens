package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Import;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Module;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.ports.Analyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes import fan-out and derives module DEPENDS_ON edges.
 * Resolution is language-independent: works from import specs + file/module keys in RepositoryModel.
 */
public final class DependencyAnalyzer implements Analyzer {

    private static final int HIGH_FAN_OUT_THRESHOLD = 25;

    @Override
    public String id() {
        return "dependencies";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        String repoId = model.repository().id();
        Map<String, String> moduleIndex = buildModuleIndex(model);
        Map<String, Integer> importsByModule = new HashMap<>();
        Map<String, Set<String>> moduleDeps = new HashMap<>();

        for (Import importDecl : model.imports()) {
            Optional<String> ownerModule = resolveOwnerModule(model, importDecl);
            ownerModule.ifPresent(moduleId ->
                    importsByModule.merge(moduleId, 1, Integer::sum));

            Optional<String> targetModule = resolveTargetModule(
                    model,
                    moduleIndex,
                    importDecl.sourceFilePath(),
                    importDecl.rawImport()
            );
            if (ownerModule.isPresent() && targetModule.isPresent()
                    && !ownerModule.get().equals(targetModule.get())) {
                moduleDeps
                        .computeIfAbsent(ownerModule.get(), ignored -> new HashSet<>())
                        .add(targetModule.get());
            }
        }

        List<Relationship> relationships = new ArrayList<>();
        int relSeq = 0;
        for (Map.Entry<String, Set<String>> entry : moduleDeps.entrySet()) {
            for (String target : entry.getValue()) {
                relationships.add(new Relationship(
                        "dep-rel-" + (++relSeq),
                        RelationshipType.DEPENDS_ON,
                        entry.getKey(),
                        target,
                        0.7,
                        Optional.of("import-resolution")
                ));
            }
        }

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("import_count", model.imports().size(), Optional.empty(), Optional.of(repoId)));
        metrics.add(new Metric(
                "module_dependency_edge_count",
                relationships.size(),
                Optional.empty(),
                Optional.of(repoId)
        ));

        int externalish = 0;
        for (Import importDecl : model.imports()) {
            if (resolveTargetModule(model, moduleIndex, importDecl.sourceFilePath(), importDecl.rawImport()).isEmpty()) {
                externalish++;
            }
        }
        metrics.add(new Metric("unresolved_or_external_import_count", externalish, Optional.empty(), Optional.of(repoId)));

        List<AnalysisResult.Finding> findings = new ArrayList<>();
        int findingSeq = 0;
        for (Map.Entry<String, Integer> entry : importsByModule.entrySet()) {
            metrics.add(new Metric(
                    "module_import_count",
                    entry.getValue(),
                    Optional.empty(),
                    Optional.of(entry.getKey())
            ));
            if (entry.getValue() >= HIGH_FAN_OUT_THRESHOLD) {
                String moduleName = model.findModule(entry.getKey()).map(Module::name).orElse(entry.getKey());
                findings.add(new AnalysisResult.Finding(
                        "dep-finding-" + (++findingSeq),
                        "warning",
                        "High import fan-out (" + entry.getValue() + ") in module " + moduleName,
                        Optional.of(entry.getKey())
                ));
            }
        }

        List<String> busiest = importsByModule.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(entry -> model.findModule(entry.getKey()).map(Module::name).orElse(entry.getKey())
                        + "=" + entry.getValue())
                .toList();

        String summary = "Dependencies: " + model.imports().size() + " imports, "
                + relationships.size() + " module DEPENDS_ON edges"
                + (busiest.isEmpty() ? "" : "; busiest: " + String.join(", ", busiest));

        return new AnalysisResult(id(), summary, metrics, relationships, findings);
    }

    private static Map<String, String> buildModuleIndex(RepositoryModel model) {
        Map<String, String> index = new HashMap<>();
        for (Module module : model.modules()) {
            for (String key : aliasKeys(module.name())) {
                index.putIfAbsent(key, module.id());
            }
        }
        for (SourceFile file : model.files()) {
            String fileKey = stripExtension(file.path().replace('\\', '/'));
            if (fileKey.endsWith("/index")) {
                fileKey = fileKey.substring(0, fileKey.length() - "/index".length());
            }
            Optional<String> owner = ownerModuleForFile(model, file.path());
            if (owner.isPresent()) {
                for (String key : aliasKeys(fileKey)) {
                    index.putIfAbsent(key, owner.get());
                }
            }
        }
        return index;
    }

    private static Optional<String> resolveOwnerModule(RepositoryModel model, Import importDecl) {
        return ownerModuleForFile(model, importDecl.sourceFilePath())
                .or(() -> model.modules().stream()
                        .filter(module -> {
                            String slash = module.name().replace('.', '/');
                            String path = importDecl.sourceFilePath().replace('\\', '/');
                            return path.equals(module.name())
                                    || path.startsWith(module.name() + "/")
                                    || path.equals(slash)
                                    || path.startsWith(slash + "/")
                                    || path.contains("/" + slash + "/")
                                    || path.contains(module.name().replace('.', '/'));
                        })
                        .map(Module::id)
                        .findFirst());
    }

    private static Optional<String> ownerModuleForFile(RepositoryModel model, String filePath) {
        return model.symbols().stream()
                .filter(symbol -> symbol.location().filePath().equals(filePath))
                .map(symbol -> symbol.moduleId())
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> {
                    String key = stripExtension(filePath.replace('\\', '/'));
                    return model.modules().stream()
                            .filter(module -> module.name().equals(key)
                                    || module.name().replace('.', '/').equals(key))
                            .map(Module::id)
                            .findFirst();
                });
    }

    private static Optional<String> resolveTargetModule(
            RepositoryModel model,
            Map<String, String> moduleIndex,
            String sourceFilePath,
            String rawImport
    ) {
        String spec = normalizeImportSpec(rawImport);
        if (spec.isBlank()) {
            return Optional.empty();
        }

        List<String> candidates = new ArrayList<>();
        if (spec.startsWith(".")) {
            // JS/TS: ./x or ../x — Python: .module or ..pkg.sub
            String relativeSpec = toRelativePathSpec(spec);
            candidates.addAll(resolveRelative(sourceFilePath, relativeSpec));
        } else {
            candidates.add(spec);
            candidates.add(spec.replace('.', '/'));
            candidates.add(spec.replace('/', '.'));
            int firstDot = spec.indexOf('.');
            if (firstDot > 0) {
                candidates.add(spec.substring(0, firstDot));
            }
        }

        for (String candidate : candidates) {
            for (String key : aliasKeys(candidate)) {
                String moduleId = moduleIndex.get(key);
                if (moduleId != null) {
                    return Optional.of(moduleId);
                }
            }
        }

        // Longest module-name prefix match for Java-style packages.
        return model.modules().stream()
                .sorted(Comparator.comparingInt((Module module) -> module.name().length()).reversed())
                .filter(module -> {
                    String name = module.name();
                    return spec.equals(name)
                            || spec.startsWith(name + ".")
                            || spec.replace('/', '.').equals(name)
                            || spec.replace('/', '.').startsWith(name + ".");
                })
                .map(Module::id)
                .findFirst();
    }

    /**
     * Converts import specs that start with '.' into a path-style relative form.
     * Leaves already path-style relatives (./x, ../x) unchanged; maps Python-style
     * ".mod" / "..pkg.sub" onto "./mod" / "../pkg/sub".
     */
    private static String toRelativePathSpec(String spec) {
        if (spec.startsWith("./") || spec.startsWith("../")) {
            return spec;
        }
        int dots = 0;
        while (dots < spec.length() && spec.charAt(dots) == '.') {
            dots++;
        }
        String rest = spec.substring(dots).replace('.', '/');
        StringBuilder prefix = new StringBuilder();
        if (dots <= 1) {
            prefix.append("./");
        } else {
            for (int i = 0; i < dots - 1; i++) {
                prefix.append("../");
            }
        }
        return prefix + rest;
    }

    private static List<String> resolveRelative(String sourceFilePath, String spec) {
        String sourceDir = sourceFilePath.replace('\\', '/');
        int slash = sourceDir.lastIndexOf('/');
        sourceDir = slash >= 0 ? sourceDir.substring(0, slash) : "";
        Path resolved = Path.of(sourceDir).resolve(spec).normalize();
        String normalized = resolved.toString().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        List<String> out = new ArrayList<>();
        out.add(normalized);
        out.add(stripExtension(normalized));
        if (normalized.endsWith("/index") || normalized.endsWith("/index.js") || normalized.endsWith("/index.ts")) {
            out.add(stripExtension(normalized.replaceAll("/index(\\.[^.]+)?$", "")));
        }
        return out;
    }

    private static Set<String> aliasKeys(String value) {
        Set<String> keys = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return keys;
        }
        String base = stripExtension(value.replace('\\', '/'));
        keys.add(base);
        keys.add(base.replace('/', '.'));
        keys.add(base.replace('.', '/'));
        keys.add(base.toLowerCase(Locale.ROOT));
        return keys;
    }

    private static String normalizeImportSpec(String rawImport) {
        String value = rawImport == null ? "" : rawImport.trim();
        value = value.replaceFirst("^import\\s+(?:static\\s+)?", "");
        value = value.replaceFirst("^export\\s+(?:type\\s+)?.*\\bfrom\\s+", "");
        value = value.replaceFirst("^from\\s+", "");
        value = value.replace(";", "").trim();
        if ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        // Keep only the module path for "pkg import x" leftovers.
        if (value.contains(" import ")) {
            value = value.substring(0, value.indexOf(" import ")).trim();
        }
        return value.trim();
    }

    /**
     * Strip a filename extension from a path. Dotted package/module names without
     * a path separator (e.g. {@code demo.core}, {@code demo.core.Core}) are left intact.
     */
    private static String stripExtension(String path) {
        String value = path.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash < 0) {
            return value;
        }
        int dot = value.lastIndexOf('.');
        if (dot > slash) {
            return value.substring(0, dot);
        }
        return value;
    }
}
