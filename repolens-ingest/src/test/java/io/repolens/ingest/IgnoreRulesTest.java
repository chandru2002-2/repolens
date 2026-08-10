package io.repolens.ingest;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoreRulesTest {

    @Test
    void ignoresDefaultDirectoriesAndGlobs() {
        IgnoreRules rules = IgnoreRules.ofDefaults();

        assertTrue(rules.isIgnored(Path.of("node_modules"), true));
        assertTrue(rules.isIgnored(Path.of("node_modules/left-pad/index.js"), false));
        assertTrue(rules.isIgnored(Path.of(".git/config"), false));
        assertTrue(rules.isIgnored(Path.of("target/classes/Foo.class"), false));
        assertTrue(rules.isIgnored(Path.of("App.class"), false));
        assertFalse(rules.isIgnored(Path.of("src/main/java/App.java"), false));
    }

    @Test
    void supportsNegation() {
        IgnoreRules rules = IgnoreRules.builder()
                .addPattern("*.log")
                .addPattern("!keep.log")
                .build();

        assertTrue(rules.isIgnored(Path.of("debug.log"), false));
        assertFalse(rules.isIgnored(Path.of("keep.log"), false));
    }
}
