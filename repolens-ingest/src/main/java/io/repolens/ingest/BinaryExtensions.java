package io.repolens.ingest;

import java.util.Locale;
import java.util.Set;

/**
 * Extension heuristics for assets that should not enter source analysis inventory
 * when under the per-file size limit. Oversized files are handled separately via
 * {@code maxFileBytes} skips so they can be reported explicitly.
 */
final class BinaryExtensions {

    private static final Set<String> EXTENSIONS = Set.of(
            ".gif", ".png", ".jpg", ".jpeg", ".webp", ".ico", ".bmp", ".tif", ".tiff",
            ".mp3", ".mp4", ".mov", ".avi", ".mkv", ".wav", ".flac",
            ".zip", ".tar", ".gz", ".tgz", ".7z", ".rar", ".jar", ".war", ".ear",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".woff", ".woff2", ".ttf", ".otf", ".eot",
            ".bin", ".dat", ".exe", ".dll", ".so", ".dylib", ".class"
    );

    private BinaryExtensions() {
    }

    static boolean isBinaryPath(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT).replace('\\', '/');
        int slash = lower.lastIndexOf('/');
        String name = slash >= 0 ? lower.substring(slash + 1) : lower;
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return EXTENSIONS.contains(name.substring(dot));
    }
}
