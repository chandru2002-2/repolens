package io.repolens.analyzers.context;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SourceFile;
import io.repolens.core.model.SourceLocation;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesArchitectureMarkdownWithinBudget() throws Exception {
        RepositoryModel model = sampleModel();
        ContextGenerator generator = new ContextGenerator();
        ContextResult result = generator.generate(
                model,
                List.of(new AnalysisResult("structure", "structure ok", List.of(), List.of(), List.of())),
                tempDir,
                new ContextRequest(
                        ContextPurpose.ARCHITECTURE,
                        ContextScope.entireRepository(),
                        ContextBudget.of(8_000),
                        ContextFormat.MARKDOWN,
                        null
                )
        );
        assertEquals(ContextPurpose.ARCHITECTURE, result.purpose());
        assertEquals(8_000, result.tokenBudget());
        assertTrue(result.estimatedTokens() > 0);
        assertTrue(result.estimatedTokens() <= result.tokenBudget() + 500);
        assertTrue(result.content().contains("# Repository Context"));
        assertTrue(result.content().contains("ARCHITECTURE"));
        assertTrue(result.tokenEstimateApproximate());
        assertFalse(result.included().isEmpty());
    }

    @Test
    void generatesJsonFormat() throws Exception {
        ContextResult result = new ContextGenerator().generate(
                sampleModel(),
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.FULL_REPOSITORY,
                        ContextScope.entireRepository(),
                        ContextBudget.of(4_000),
                        ContextFormat.JSON,
                        "Full"
                )
        );
        assertTrue(result.content().trim().startsWith("{"));
        assertTrue(result.content().contains("\"purpose\""));
        assertEquals("json", result.format().name().toLowerCase());
    }

    @Test
    void selectedSymbolsRequireIds() {
        assertThrows(IllegalArgumentException.class, () -> new ContextGenerator().generate(
                sampleModel(),
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.SELECTED_SYMBOLS,
                        ContextScope.entireRepository(),
                        ContextBudget.of(2_000),
                        ContextFormat.MARKDOWN,
                        null
                )
        ));
    }

    @Test
    void selectedSymbolContextIncludesNeighborhoodWithoutFabrication() throws Exception {
        RepositoryModel model = sampleModel();
        ContextResult result = new ContextGenerator().generate(
                model,
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.SELECTED_SYMBOLS,
                        new ContextScope(
                                ContextScope.ScopeMode.SELECTED_SYMBOLS,
                                List.of(),
                                List.of("sym:c"),
                                List.of()
                        ),
                        ContextBudget.of(4_000),
                        ContextFormat.MARKDOWN,
                        null
                )
        );
        assertTrue(result.content().contains("UserController"));
        assertTrue(result.content().contains("UserService"));
        assertFalse(result.content().contains("InventedService"));
    }

    @Test
    void tinyBudgetExcludesLowerPriorityContent() throws Exception {
        Files.createDirectories(tempDir.resolve("c"));
        Files.writeString(tempDir.resolve("c/UserController.java"), "class UserController { void a(){} void b(){} }".repeat(40));
        ContextResult result = new ContextGenerator().generate(
                sampleModel(),
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.FULL_REPOSITORY,
                        ContextScope.entireRepository(),
                        ContextBudget.of(500),
                        ContextFormat.MARKDOWN,
                        null
                )
        );
        assertTrue(result.estimatedTokens() <= 700);
        assertTrue(result.excluded().stream().anyMatch(e -> e.toLowerCase().contains("omit")
                || e.toLowerCase().contains("truncated")
                || e.toLowerCase().contains("budget")
                || e.toLowerCase().contains("sensitive")
                || e.toLowerCase().contains("additional"))
                || result.content().contains("truncated"));
    }

    @Test
    void invalidBudgetRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(10));
        assertThrows(IllegalArgumentException.class, () -> ContextBudget.of(500_000));
    }

    @Test
    void redactsSecretLikeAssignmentsInSnippets() throws Exception {
        Files.createDirectories(tempDir.resolve("c"));
        Files.writeString(tempDir.resolve("c/UserController.java"),
                "public class UserController {\n  String api_key = \"sk-super-secret-value-123456\";\n}\n");
        ContextResult result = new ContextGenerator().generate(
                sampleModel(),
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.SELECTED_FILES,
                        new ContextScope(
                                ContextScope.ScopeMode.SELECTED_FILES,
                                List.of("c/UserController.java"),
                                List.of(),
                                List.of()
                        ),
                        ContextBudget.of(4_000),
                        ContextFormat.MARKDOWN,
                        null
                )
        );
        assertFalse(result.content().contains("sk-super-secret-value-123456"));
        assertTrue(result.content().contains("REDACTED") || result.content().contains("UserController"));
    }

    @Test
    void apiBackendUsesEndpointFacts() throws Exception {
        ContextResult result = new ContextGenerator().generate(
                sampleModel(),
                List.of(),
                tempDir,
                new ContextRequest(
                        ContextPurpose.API_BACKEND,
                        ContextScope.entireRepository(),
                        ContextBudget.of(4_000),
                        ContextFormat.MARKDOWN,
                        null
                )
        );
        assertTrue(result.content().contains("Get Users") || result.content().contains("Endpoints"));
    }

    @Test
    void approxEstimatorIsDeterministic() {
        TokenEstimator e = TokenEstimator.ApproxCharsPerToken.INSTANCE;
        assertEquals(e.estimate("abcd"), e.estimate("abcd"));
        assertEquals(1, e.estimate("ab"));
        assertEquals(3, e.estimate("abcdefghijkk"));
    }

    @Test
    void tryAnotherStrategiesProduceDifferentDeterministicPackages() throws Exception {
        RepositoryModel model = sampleModel();
        ContextGenerator generator = new ContextGenerator();
        ContextResult overview = generator.generate(model, List.of(), tempDir, request(ContextStrategy.ARCHITECTURE_OVERVIEW));
        ContextResult source = generator.generate(model, List.of(), tempDir, request(ContextStrategy.SOURCE_AND_SYMBOLS));
        ContextResult compact = generator.generate(model, List.of(), tempDir, request(ContextStrategy.COMPACT_ARCHITECTURE));
        ContextResult alternate = generator.generate(model, List.of(), tempDir, request(ContextStrategy.ALTERNATE_PRIORITIZATION));

        assertEquals("Architecture Overview", overview.title());
        assertEquals("Architecture + Source", source.title());
        assertEquals("Compact Architecture", compact.title());
        assertEquals("Reordered Detail", alternate.title());
        assertTrue(overview.content().contains("relationships") || overview.included().stream().anyMatch(i -> i.contains("relationship")));
        assertTrue(source.content().contains("UserController.java") || source.included().stream().anyMatch(i -> i.contains("source")));
        assertTrue(compact.excluded().stream().anyMatch(e -> e.toLowerCase().contains("source")));
        assertTrue(compact.content().contains("Get Users") || compact.content().contains("Endpoints"));
        assertFalse(overview.content().equals(source.content()));
        assertFalse(source.content().equals(compact.content()));
        assertFalse(compact.content().equals(alternate.content()));
        assertEquals(overview.content(), generator.generate(model, List.of(), tempDir, request(ContextStrategy.ARCHITECTURE_OVERVIEW)).content());
        assertEquals(ContextStrategy.SOURCE_AND_SYMBOLS, ContextStrategy.ARCHITECTURE_OVERVIEW.next());
        assertEquals(ContextStrategy.ARCHITECTURE_OVERVIEW, ContextStrategy.ALTERNATE_PRIORITIZATION.next());
    }

    private ContextRequest request(ContextStrategy strategy) {
        return new ContextRequest(
                ContextPurpose.ARCHITECTURE,
                ContextScope.entireRepository(),
                ContextBudget.of(8_000),
                ContextFormat.MARKDOWN,
                null,
                strategy
        );
    }
        Files.createDirectories(tempDir.resolve("c"));
        Files.createDirectories(tempDir.resolve("s"));
        if (!Files.exists(tempDir.resolve("c/UserController.java"))) {
            Files.writeString(tempDir.resolve("c/UserController.java"),
                    "public class UserController { UserService userService; void run(){ userService.find(); } }\n");
        }
        Files.writeString(tempDir.resolve("s/UserService.java"),
                "public class UserService { void find(){} }\n");

        return RepositoryModel.builder(Repository.local("r1", "demo", tempDir.toString()))
                .addFile(new SourceFile("c/UserController.java", "java", "h1", 40))
                .addFile(new SourceFile("s/UserService.java", "java", "h2", 30))
                .addSymbol(new Symbol(
                        "sym:c", "UserController", SymbolKind.CLASS,
                        Optional.empty(), Optional.empty(),
                        new SourceLocation("c/UserController.java", 1, 0, 10, 1), Optional.empty()))
                .addSymbol(new Symbol(
                        "sym:s", "UserService", SymbolKind.CLASS,
                        Optional.empty(), Optional.empty(),
                        new SourceLocation("s/UserService.java", 1, 0, 5, 1), Optional.empty()))
                .addRelationship(new Relationship(
                        "rel:1", RelationshipType.CALLS, "sym:c", "sym:s", 0.9, Optional.of("call:find")))
                .addStructuralFact(new StructuralFact(
                        "f1", "sequence", "controller", "UserController",
                        Optional.of("sym:c"), Optional.empty(), Optional.of("role=controller"),
                        Optional.of("c/UserController.java"), Optional.of(1)))
                .addStructuralFact(new StructuralFact(
                        "f2", "sequence", "service", "UserService",
                        Optional.of("sym:s"), Optional.empty(), Optional.of("role=service"),
                        Optional.of("s/UserService.java"), Optional.of(1)))
                .addStructuralFact(new StructuralFact(
                        "f3", "usecase", "use_case", "Get Users",
                        Optional.of("sym:c"), Optional.empty(), Optional.of("route=/users"),
                        Optional.of("c/UserController.java"), Optional.of(2)))
                .addStructuralFact(new StructuralFact(
                        "f4", "er", "entity", "User",
                        Optional.empty(), Optional.empty(), Optional.of("table=users"),
                        Optional.empty(), Optional.empty()))
                .build();
    }
}
