package io.repolens.parse.engine;

import java.util.List;

/**
 * Executes Tree-sitter (or compatible) parse+query for a language id.
 */
public interface SyntaxQueryEngine {

    String id();

    boolean isAvailable();

    List<SyntaxCapture> query(String treeSitterLanguage, String source, String query);
}
