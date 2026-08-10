package io.repolens.parse;

import io.repolens.parse.engine.SeartTreeSitterEngine;
import io.repolens.parse.engine.SyntaxQueryEngine;

/**
 * Public entry points for the parse module.
 */
public final class ParseModule {
    private ParseModule() {
    }

    public static ProfiledSourceAnalyzer sourceAnalyzer() {
        return new ProfiledSourceAnalyzer();
    }

    public static ProfiledSourceAnalyzer sourceAnalyzer(SyntaxQueryEngine engine) {
        return new ProfiledSourceAnalyzer(engine, io.repolens.parse.profile.LanguageProfiles.defaults());
    }

    public static SyntaxQueryEngine treeSitterEngine() {
        return new SeartTreeSitterEngine();
    }
}
