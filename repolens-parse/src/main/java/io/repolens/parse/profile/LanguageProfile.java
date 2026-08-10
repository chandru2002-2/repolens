package io.repolens.parse.profile;

import io.repolens.parse.engine.SyntaxCapture;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Thin language profile: extension routing + Tree-sitter query + normalization hints.
 * Language-specific syntax stays here, not in core/analyzers/CLI.
 */
public interface LanguageProfile {

    String id();

    String treeSitterLanguage();

    Set<String> extensions();

    /**
     * Tree-sitter query used when the native engine is available.
     */
    String query();

    default boolean supports(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return extensions().contains(lower.substring(dot));
    }

    /**
     * Structural fallback used when Tree-sitter natives cannot load.
     * Must emit the same capture names as {@link #query()}.
     */
    List<SyntaxCapture> fallbackExtract(String source);

    default Optional<String> moduleFromCaptures(List<SyntaxCapture> captures, String relativePath) {
        return captures.stream()
                .filter(c -> "module".equals(c.name()))
                .map(SyntaxCapture::text)
                .findFirst()
                .or(() -> Optional.ofNullable(directoryModule(relativePath)));
    }

    /**
     * File-path module identity (slash form, no extension). Useful for JS/TS/Python.
     */
    static String fileModuleName(String relativePath) {
        String path = relativePath.replace('\\', '/');
        int dot = path.lastIndexOf('.');
        if (dot > path.lastIndexOf('/')) {
            path = path.substring(0, dot);
        }
        if (path.endsWith("/index")) {
            path = path.substring(0, path.length() - "/index".length());
        }
        return path.isBlank() ? null : path;
    }

    private static String directoryModule(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        if (slash <= 0) {
            return null;
        }
        return relativePath.substring(0, slash).replace('/', '.');
    }
}
