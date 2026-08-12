package io.repolens.parse;

import io.repolens.core.model.DocumentationSection;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.LanguageProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritanceAndDocumentationParseTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsInheritanceFieldsMethodsAndDocumentationLinks() throws Exception {
        Path pkg = tempDir.resolve("src/com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Base.java"), """
                package com.example;
                public class Base {
                  private String name;
                  public String getName() { return name; }
                }
                """);
        Files.writeString(pkg.resolve("UserService.java"), """
                package com.example;
                import com.example.Base;
                public class UserService extends Base {
                  private int count;
                  public void createUser() {}
                  public void findUser() {}
                }
                """);
        Files.writeString(pkg.resolve("RunnableJob.java"), """
                package com.example;
                public interface Job {
                  void run();
                }
                public class RunnableJob implements Job {
                  public void run() {}
                }
                """);
        Files.writeString(tempDir.resolve("README.md"), """
                # Demo

                ## Authentication

                `UserService` handles user-related business logic.

                See also `com.example.Base`.
                """);
        Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("docs/architecture.md"), """
                # Architecture

                The `Job` interface defines runnable work units.
                """);

        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(
                        file("src/com/example/Base.java"),
                        file("src/com/example/UserService.java"),
                        file("src/com/example/RunnableJob.java"),
                        file("README.md"),
                        file("docs/architecture.md")
                ),
                500,
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

        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("UserService") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("name") && s.kind() == SymbolKind.FIELD));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("createUser") && s.kind() == SymbolKind.METHOD));
        assertTrue(model.relationships().stream().anyMatch(r ->
                r.type() == RelationshipType.EXTENDS));
        assertTrue(model.relationships().stream().anyMatch(r ->
                r.type() == RelationshipType.IMPLEMENTS));

        assertFalse(model.documentation().isEmpty());
        assertTrue(model.documentation().stream().anyMatch(d -> d.path().equals("README.md")));
        assertTrue(model.documentationReferences().stream().anyMatch(ref ->
                ref.matchedText().equals("UserService") || ref.matchedText().endsWith(".UserService")));
        assertTrue(model.documentationReferences().stream().anyMatch(ref ->
                ref.matchedText().equals("Job") || ref.matchedText().contains("Job")));
    }

    @Test
    void missingReadmeAndDocsYieldEmptyDocumentation() throws Exception {
        Path javaFile = tempDir.resolve("Hello.java");
        Files.writeString(javaFile, "public class Hello {}");
        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(file("Hello.java")),
                20,
                0
        );
        RepositoryModel model = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);

        assertTrue(model.documentation().isEmpty());
        assertTrue(model.documentationReferences().isEmpty());
    }

    @Test
    void documentationWithoutMatchingEntityCreatesNoReferences() throws Exception {
        Files.writeString(tempDir.resolve("Hello.java"), "public class Hello {}");
        Files.writeString(tempDir.resolve("README.md"), "# Notes\n\nTalks about UnrelatedThing only.\n");
        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(file("Hello.java"), file("README.md")),
                40,
                0
        );
        RepositoryModel model = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);

        assertEquals(1, model.documentation().size());
        assertTrue(model.documentationReferences().isEmpty());
    }

    @Test
    void sectionMentionRequiresDeterministicMatch() {
        DocumentationSection section = new DocumentationSection(
                "s1",
                "Authentication",
                "`UserService` handles users.",
                1
        );
        assertTrue(DocumentationIndexer.sectionMentions(section, "UserService"));
        assertFalse(DocumentationIndexer.sectionMentions(section, "Missing"));
        assertTrue(DocumentationIndexer.isDocumentationPath("README.md"));
        assertTrue(DocumentationIndexer.isDocumentationPath("docs/guide.md"));
        assertFalse(DocumentationIndexer.isDocumentationPath("src/App.java"));
    }

    private static WorkingTreeInventory.InventoriedFile file(String path) {
        return new WorkingTreeInventory.InventoriedFile(path, 100);
    }
}
