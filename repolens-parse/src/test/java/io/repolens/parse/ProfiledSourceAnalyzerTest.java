package io.repolens.parse;

import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.JavaLanguageProfile;
import io.repolens.parse.profile.LanguageProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfiledSourceAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsModelFromJavaSourcesUsingFallback() throws Exception {
        Path javaFile = tempDir.resolve("src/demo/Hello.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                package demo;
                import java.util.List;
                public class Hello {
                  public void greet() {}
                }
                """);

        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile("src/demo/Hello.java", 100)),
                100,
                0
        );

        ProfiledSourceAnalyzer analyzer = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        );

        RepositoryModel model = analyzer.analyze(
                Repository.local("r1", "demo", tempDir.toString()),
                tempDir,
                inventory
        );

        assertEquals(1, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Hello") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("greet") && s.kind() == SymbolKind.METHOD));
        assertFalse(model.imports().isEmpty());
        assertEquals("structural-fallback", analyzer.engineId());
    }

    @Test
    void nestedClassesParentToEnclosingTypeNotPackage() throws Exception {
        Path javaFile = tempDir.resolve("src/demo/Outer.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                package demo;
                public class Outer {
                  public class Inner {
                    public class Nested {}
                  }
                  public class Sibling {}
                }
                public class Other {}
                """);

        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile("src/demo/Outer.java", 100)),
                100,
                0
        );

        ProfiledSourceAnalyzer analyzer = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        );
        RepositoryModel model = analyzer.analyze(
                Repository.local("r1", "demo", tempDir.toString()),
                tempDir,
                inventory
        );

        var byName = model.symbols().stream()
                .filter(s -> s.kind() == SymbolKind.CLASS)
                .collect(java.util.stream.Collectors.toMap(io.repolens.core.model.Symbol::name, s -> s));
        assertEquals(Optional.empty(), byName.get("Outer").parentSymbolId());
        assertEquals(Optional.empty(), byName.get("Other").parentSymbolId());
        assertEquals(byName.get("Outer").id(), byName.get("Inner").parentSymbolId().orElseThrow());
        assertEquals(byName.get("Inner").id(), byName.get("Nested").parentSymbolId().orElseThrow());
        assertEquals(byName.get("Outer").id(), byName.get("Sibling").parentSymbolId().orElseThrow());
    }

    @Test
    void tabIndentedNestedClassParentsToEnclosingType() throws Exception {
        Path javaFile = tempDir.resolve("src/demo/PetControllerTests.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                package demo;
                class PetControllerTests {
                \tprivate static final int TEST_OWNER_ID = 1;
                \t@Nested
                \tclass ProcessCreationFormHasErrors {
                \t}
                \tclass ProcessUpdateFormHasErrors {
                \t}
                }
                """);

        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile("src/demo/PetControllerTests.java", 100)),
                100,
                0
        );
        ProfiledSourceAnalyzer analyzer = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        );
        RepositoryModel model = analyzer.analyze(
                Repository.local("r1", "demo", tempDir.toString()),
                tempDir,
                inventory
        );
        var byName = model.symbols().stream()
                .filter(s -> s.kind() == SymbolKind.CLASS)
                .collect(java.util.stream.Collectors.toMap(io.repolens.core.model.Symbol::name, s -> s));
        String outer = byName.get("PetControllerTests").id();
        assertEquals(outer, byName.get("ProcessCreationFormHasErrors").parentSymbolId().orElse("missing"));
        assertEquals(outer, byName.get("ProcessUpdateFormHasErrors").parentSymbolId().orElse("missing"));
    }

    @Test
    void javaFallbackExtractsExpectedCaptures() {
        var captures = new JavaLanguageProfile().fallbackExtract("""
                package demo;
                import java.util.List;
                public class Hello {
                  public void greet() {}
                }
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("module") && c.text().equals("demo")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Hello")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("greet")));
    }

    @Test
    void treeSitterEngineReportsAvailabilityWithoutCrashing() {
        ProfiledSourceAnalyzer analyzer = ParseModule.sourceAnalyzer();
        // On arm64 with x86_64-only natives this is false; on compatible platforms true.
        assertTrue(analyzer.engineId().equals("tree-sitter-seart")
                || analyzer.engineId().equals("structural-fallback"));
    }
}
