package io.repolens.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads root-level ignore files (.gitignore, .repolensignore) if present.
 */
public final class IgnoreFileLoader {

    private IgnoreFileLoader() {
    }

    public static List<String> loadRootIgnorePatterns(Path workingTree) throws IOException {
        List<String> patterns = new ArrayList<>();
        for (String name : List.of(".gitignore", ".repolensignore")) {
            Path file = workingTree.resolve(name);
            if (Files.isRegularFile(file)) {
                try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                    lines.forEach(patterns::add);
                }
            }
        }
        return patterns;
    }
}
