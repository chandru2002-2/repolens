package io.repolens.core.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryModelTest {

    @Test
    void buildsValidAggregate() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        SourceFile file = new SourceFile("src/Main.java", "java", "abc", 12);
        Module module = Module.of("mod-1", "demo", "java");
        Symbol symbol = new Symbol(
                "sym-1",
                "Main",
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.of("mod-1"),
                SourceLocation.ofFile("src/Main.java"),
                Optional.of("public")
        );

        RepositoryModel model = RepositoryModel.builder(repository)
                .addFile(file)
                .addModule(module)
                .addSymbol(symbol)
                .addRelationship(Relationship.of("rel-1", RelationshipType.CONTAINS, "mod-1", "sym-1"))
                .addMetric(new Metric("file_count", 1, Optional.empty(), Optional.of("repo-1")))
                .build();

        assertEquals(1, model.fileCount());
        assertEquals(1, model.symbolCount());
        assertTrue(model.findSymbol("sym-1").isPresent());
        assertEquals("demo", model.repository().name());
    }

    @Test
    void rejectsSymbolReferencingUnknownFile() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        Symbol symbol = new Symbol(
                "sym-1",
                "Main",
                SymbolKind.CLASS,
                Optional.empty(),
                Optional.empty(),
                SourceLocation.ofFile("missing.java"),
                Optional.empty()
        );

        assertThrows(IllegalStateException.class, () ->
                RepositoryModel.builder(repository).addSymbol(symbol).build());
    }

    @Test
    void rejectsDuplicateFilePaths() {
        Repository repository = Repository.local("repo-1", "demo", "/tmp/demo");
        SourceFile file = new SourceFile("a.java", "java", "h1", 1);

        assertThrows(IllegalArgumentException.class, () ->
                RepositoryModel.builder(repository).addFile(file).addFile(file));
    }

    @Test
    void localRepositoryRequiresPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new Repository("id", "name", RepositoryOrigin.LOCAL, Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
