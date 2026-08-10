package io.repolens.parse.engine;

import java.util.List;

/**
 * Engine that is never available — forces language-profile fallback extractors.
 */
public final class UnavailableSyntaxEngine implements SyntaxQueryEngine {
    @Override
    public String id() {
        return "unavailable";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<SyntaxCapture> query(String treeSitterLanguage, String source, String query) {
        throw new IllegalStateException("unavailable");
    }
}
