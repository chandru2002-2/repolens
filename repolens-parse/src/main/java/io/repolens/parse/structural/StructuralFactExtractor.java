package io.repolens.parse.structural;

import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.WorkingTreeInventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Facade for deterministic structural-fact extraction.
 * Delegates to focused extractors under this package.
 */
public final class StructuralFactExtractor {

    private StructuralFactExtractor() {
    }

    public static void extract(
            RepositoryModel.Builder builder,
            Path workingTree,
            WorkingTreeInventory inventory,
            List<Symbol> symbols
    ) {
        StructuralFactSink sink = new StructuralFactSink(builder, symbols);

        for (WorkingTreeInventory.InventoriedFile file : inventory.files()) {
            if (sink.factsFull()) {
                break;
            }
            String path = file.relativePath().replace('\\', '/');
            String lower = path.toLowerCase(Locale.ROOT);
            Path absolute = workingTree.resolve(file.relativePath());
            String source = read(absolute);
            if (source == null || source.isBlank()) {
                continue;
            }

            if (lower.endsWith(".java")) {
                JavaStructuralFactExtractor.extract(sink, path, source);
            } else if (DeploymentStructuralFactExtractor.isComposeFile(lower)) {
                DeploymentStructuralFactExtractor.extractCompose(sink, path, source);
            } else if (lower.endsWith("dockerfile")
                    || Path.of(lower).getFileName().toString().equals("dockerfile")) {
                DeploymentStructuralFactExtractor.extractDockerfile(sink, path, source);
            } else if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
                DeploymentStructuralFactExtractor.extractKubernetes(sink, path, source);
                ConfigStructuralFactExtractor.extractDatasourceHint(sink, path, source);
            }
        }

        sink.flushCalls();
    }

    private static String read(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }
}
