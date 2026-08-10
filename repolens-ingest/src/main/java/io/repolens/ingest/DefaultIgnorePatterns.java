package io.repolens.ingest;

import java.util.List;

/**
 * Built-in ignore patterns applied before repository .gitignore.
 */
public final class DefaultIgnorePatterns {

    public static final List<String> PATTERNS = List.of(
            ".git/",
            ".svn/",
            ".hg/",
            ".idea/",
            ".vscode/",
            ".gradle/",
            "node_modules/",
            "bower_components/",
            "vendor/",
            "target/",
            "build/",
            "dist/",
            "out/",
            "coverage/",
            "__pycache__/",
            ".tox/",
            ".venv/",
            "venv/",
            ".DS_Store",
            "*.class",
            "*.jar",
            "*.war",
            "*.o",
            "*.so",
            "*.dylib",
            "*.exe"
    );

    private DefaultIgnorePatterns() {
    }
}
