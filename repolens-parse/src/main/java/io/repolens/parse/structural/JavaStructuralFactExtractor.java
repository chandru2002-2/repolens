package io.repolens.parse.structural;

import io.repolens.core.model.Symbol;

/**
 * Coordinates Java source structural-fact extractors for a single file.
 */
public final class JavaStructuralFactExtractor {

    private JavaStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source) {
        Symbol owner = sink.typeByFile().get(path);
        JpaStructuralFactExtractor.extract(sink, path, source, owner);
        SpringStructuralFactExtractor.extract(sink, path, source, owner);
        CallStructuralFactExtractor.extract(sink, path, source, owner);
        ActivityStructuralFactExtractor.extract(sink, path, source, owner);
        StateStructuralFactExtractor.extract(sink, path, source, owner);
    }
}
