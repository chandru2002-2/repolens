package io.repolens.analyzers.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptGeneratorTest {

    @Test
    void buildsDeterministicPromptWithRequiredSections() {
        ContextResult context = sampleContext();
        AiTask task = AiTask.ofPreset(AiTaskPreset.EXPLAIN_AUTHENTICATION_SECURITY);

        AiPromptResult first = new AiPromptGenerator().generate(task, context);
        AiPromptResult second = new AiPromptGenerator().generate(task, context);

        assertEquals(first.prompt(), second.prompt());
        assertEquals(AiTaskPreset.EXPLAIN_AUTHENTICATION_SECURITY, first.preset());
        assertTrue(first.taskText().toLowerCase().contains("authentication"));
        assertTrue(first.prompt().startsWith("INSTRUCTIONS:"));
        assertTrue(first.prompt().contains("TASK:"));
        assertTrue(first.prompt().contains("RELEVANT SCOPE:"));
        assertTrue(first.prompt().contains("REPOSITORY CONTEXT:"));
        assertTrue(first.prompt().contains("ANALYSIS NOTES:"));
        assertTrue(first.prompt().contains("LIMITATIONS:"));
        assertEquals(1, AiPromptGenerator.countRepositoryContextSections(first.prompt()));
        assertTrue(first.prompt().contains("Packaging strategy: Architecture Overview"));
        assertFalse(first.prompt().contains("REPOSITORY CONTEXT:\nREPOSITORY CONTEXT:"));
    }

    @Test
    void customTaskUsesFreeText() {
        AiTask task = AiTask.custom(
                "Explain how authentication works in this repository and trace the request from login through JWT validation.");
        AiPromptResult result = new AiPromptGenerator().generate(task, sampleContext());
        assertTrue(result.prompt().contains("login through JWT validation"));
        assertEquals(AiTaskPreset.CUSTOM, result.preset());
        assertEquals(1, AiPromptGenerator.countRepositoryContextSections(result.prompt()));
    }

    @Test
    void customPresetRequiresText() {
        assertThrows(IllegalArgumentException.class, () -> new AiTask(AiTaskPreset.CUSTOM, "  "));
    }

    @Test
    void stripsDuplicateRepositoryContextHeadingFromEmbeddedBody() {
        ContextResult context = new ContextResult(
                "ctx-2",
                "Auth context",
                ContextPurpose.SECURITY,
                ContextScope.entireRepository(),
                ContextFormat.MARKDOWN,
                8_000,
                1_200,
                "# Repository Context\n\nREPOSITORY CONTEXT:\n- JwtFilter\n",
                List.of(),
                List.of(),
                true,
                ContextStrategy.SOURCE_AND_SYMBOLS
        );
        AiPromptResult result = new AiPromptGenerator().generate(
                AiTask.ofPreset(AiTaskPreset.EXPLAIN_ARCHITECTURE), context);
        assertEquals(1, AiPromptGenerator.countRepositoryContextSections(result.prompt()));
        assertTrue(result.prompt().contains("JwtFilter"));
    }

    @Test
    void suggestedPurposeMapsPresets() {
        assertEquals(ContextPurpose.ARCHITECTURE, AiTaskPreset.EXPLAIN_ARCHITECTURE.suggestedPurpose());
        assertEquals(ContextPurpose.SECURITY, AiTaskPreset.EXPLAIN_AUTHENTICATION_SECURITY.suggestedPurpose());
        assertEquals(ContextPurpose.SELECTED_SYMBOLS, AiTaskPreset.EXPLAIN_SELECTED_CLASS_SYMBOL.suggestedPurpose());
        assertEquals(ContextPurpose.API_BACKEND, AiTaskPreset.TRACE_API_REQUEST.suggestedPurpose());
        assertEquals(ContextPurpose.DATABASE_JPA, AiTaskPreset.EXPLAIN_DATABASE_JPA.suggestedPurpose());
    }

    private static ContextResult sampleContext() {
        return new ContextResult(
                "ctx-1",
                "Auth context",
                ContextPurpose.SECURITY,
                ContextScope.entireRepository(),
                ContextFormat.MARKDOWN,
                8_000,
                1_200,
                "# Repository Context\n\n## Security\n- JwtFilter\n",
                List.of("repository overview", "configuration"),
                List.of("2 additional source files (cap)"),
                true,
                ContextStrategy.ARCHITECTURE_OVERVIEW
        );
    }
}
