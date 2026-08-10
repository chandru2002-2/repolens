package io.repolens.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal ignore matcher for directory names, path prefixes, and simple globs.
 * Supports a practical subset of .gitignore semantics for v1 local ingest.
 */
public final class IgnoreRules {

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static IgnoreRules ofDefaults() {
        return builder().addDefaults().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isIgnored(Path relativePath, boolean directory) {
        Objects.requireNonNull(relativePath, "relativePath");
        String normalized = normalize(relativePath);
        if (normalized.isEmpty()) {
            return false;
        }

        boolean ignored = false;
        for (Rule rule : rules) {
            if (rule.matches(normalized, directory)) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    private static String normalize(Path relativePath) {
        String value = relativePath.toString().replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();

        public Builder addDefaults() {
            for (String pattern : DefaultIgnorePatterns.PATTERNS) {
                addPattern(pattern);
            }
            return this;
        }

        public Builder addPattern(String rawPattern) {
            if (rawPattern == null) {
                return this;
            }
            String pattern = rawPattern.trim();
            if (pattern.isEmpty() || pattern.startsWith("#")) {
                return this;
            }

            boolean negated = pattern.startsWith("!");
            if (negated) {
                pattern = pattern.substring(1).trim();
                if (pattern.isEmpty()) {
                    return this;
                }
            }

            boolean directoryOnly = pattern.endsWith("/");
            if (directoryOnly) {
                pattern = pattern.substring(0, pattern.length() - 1);
            }
            if (pattern.startsWith("/")) {
                pattern = pattern.substring(1);
            }
            if (pattern.isEmpty()) {
                return this;
            }

            rules.add(new Rule(pattern, directoryOnly, negated));
            return this;
        }

        public Builder addPatterns(Iterable<String> patterns) {
            for (String pattern : patterns) {
                addPattern(pattern);
            }
            return this;
        }

        public IgnoreRules build() {
            return new IgnoreRules(rules);
        }
    }

    private record Rule(String pattern, boolean directoryOnly, boolean negated) {
        boolean matches(String relativePath, boolean directory) {
            return pathMatches(pattern, relativePath, directory, directoryOnly);
        }

        private static boolean pathMatches(
                String pattern,
                String path,
                boolean directory,
                boolean directoryOnly
        ) {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            String lowerPath = path.toLowerCase(Locale.ROOT);

            if (!lowerPattern.contains("*") && !lowerPattern.contains("?")) {
                if (lowerPath.equals(lowerPattern)) {
                    // Directory-only patterns ignore the directory itself, not a same-named file.
                    return !directoryOnly || directory;
                }
                if (lowerPath.startsWith(lowerPattern + "/")) {
                    // Nested under a directory pattern — ignore files and dirs alike.
                    return true;
                }
                for (String segment : lowerPath.split("/")) {
                    if (segment.equals(lowerPattern)) {
                        return true;
                    }
                }
                return false;
            }

            if (directoryOnly && !directory && !lowerPath.contains("/")) {
                return false;
            }

            String candidate = directory && !lowerPath.endsWith("/") ? lowerPath + "/" : lowerPath;
            return globMatch(lowerPattern, candidate) || globMatch(lowerPattern, lowerPath);
        }

        private static boolean globMatch(String pattern, String value) {
            return value.matches(globToRegex(pattern));
        }

        private static String globToRegex(String pattern) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                switch (c) {
                    case '*' -> regex.append(".*");
                    case '?' -> regex.append('.');
                    case '.' -> regex.append("\\.");
                    case '/' -> regex.append('/');
                    default -> {
                        if ("\\[]{}()+-^$|".indexOf(c) >= 0) {
                            regex.append('\\');
                        }
                        regex.append(c);
                    }
                }
            }
            regex.append('$');
            return regex.toString();
        }
    }
}
