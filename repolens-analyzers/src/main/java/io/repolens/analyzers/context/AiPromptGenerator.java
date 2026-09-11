package io.repolens.analyzers.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds a ready-to-copy AI prompt from an {@link AiTask} and existing {@link ContextResult}.
 * Deterministic and local — never calls an external AI provider.
 */
public final class AiPromptGenerator {

    static final List<String> DEFAULT_LIMITATIONS = List.of(
            "RepoLens uses static analysis only; runtime behavior is not observed.",
            "Relationships and calls appear only when supported by parsed/structural evidence.",
            "Call resolution is heuristic and may omit unresolved receivers.",
            "Token counts in the context package are approximate (~4 characters per token).",
            "Source snippets may be truncated or omitted due to token budget and sensitivity filters."
    );

    private static final Pattern REPOSITORY_CONTEXT_HEADING = Pattern.compile(
            "(?im)^(?:#+\\s*)?repository context\\s*:?\\s*$\\n?");

    public AiPromptResult generate(AiTask task, ContextResult context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");

        String taskText = task.resolvedTaskText();
        String body = repositoryContextBody(context.content());

        StringBuilder sb = new StringBuilder();
        sb.append("INSTRUCTIONS:\n");
        sb.append("You are helping a developer understand an unfamiliar repository.\n\n");
        sb.append("Use ONLY the repository context provided below.\n\n");
        sb.append("Do not invent files, symbols, relationships, runtime behavior, or implementation details ")
                .append("that are not supported by the provided context.\n\n");

        sb.append("TASK:\n");
        sb.append(taskText).append("\n\n");

        sb.append("RELEVANT SCOPE:\n");
        sb.append("- Purpose: ").append(context.purpose().name()).append('\n');
        sb.append("- Packaging strategy: ").append(context.strategy().displayName()).append('\n');
        sb.append("- Scope mode: ").append(context.scope().mode().name()).append('\n');
        if (!context.scope().symbolIds().isEmpty()) {
            sb.append("- Symbol ids: ").append(String.join(", ", context.scope().symbolIds())).append('\n');
        }
        if (!context.scope().filePaths().isEmpty()) {
            sb.append("- File paths: ").append(String.join(", ", context.scope().filePaths())).append('\n');
        }
        if (!context.scope().graphNodeIds().isEmpty()) {
            sb.append("- Graph node ids: ").append(String.join(", ", context.scope().graphNodeIds())).append('\n');
        }
        sb.append("- Token budget: ").append(context.tokenBudget()).append('\n');
        sb.append("- Estimated context tokens: ").append(context.estimatedTokens());
        if (context.tokenEstimateApproximate()) {
            sb.append(" (approximate)");
        }
        sb.append("\n\n");

        sb.append("REPOSITORY CONTEXT:\n");
        sb.append(body).append("\n\n");

        sb.append("ANALYSIS NOTES:\n");
        for (String note : analysisNotes(task, context)) {
            sb.append("- ").append(note).append('\n');
        }
        sb.append('\n');

        sb.append("LIMITATIONS:\n");
        for (String limitation : DEFAULT_LIMITATIONS) {
            sb.append("- ").append(limitation).append('\n');
        }
        if (!context.excluded().isEmpty()) {
            sb.append("- Context exclusions: ")
                    .append(context.excluded().stream().limit(12).collect(Collectors.joining("; ")));
            if (context.excluded().size() > 12) {
                sb.append("; …");
            }
            sb.append('\n');
        }

        String prompt = sb.toString().trim() + "\n";
        if (countRepositoryContextSections(prompt) != 1) {
            throw new IllegalStateException("prompt must contain exactly one REPOSITORY CONTEXT section");
        }
        return new AiPromptResult(task.preset(), taskText, prompt);
    }

    static String repositoryContextBody(String content) {
        String body = content == null ? "" : content.trim();
        body = REPOSITORY_CONTEXT_HEADING.matcher(body).replaceFirst("");
        body = REPOSITORY_CONTEXT_HEADING.matcher(body).replaceAll("");
        return body.trim();
    }

    static int countRepositoryContextSections(String prompt) {
        String upper = prompt.toUpperCase(Locale.ROOT);
        int count = 0;
        int idx = 0;
        while (true) {
            int found = upper.indexOf("REPOSITORY CONTEXT:", idx);
            if (found < 0) {
                return count;
            }
            count++;
            idx = found + "REPOSITORY CONTEXT:".length();
        }
    }

    private static List<String> analysisNotes(AiTask task, ContextResult context) {
        List<String> notes = new ArrayList<>();
        notes.add("AI task preset: " + task.preset().name() + " (" + task.preset().label() + ")");
        notes.add("Context purpose: " + context.purpose().name());
        notes.add("Packaging strategy: " + context.strategy().displayName());
        notes.add("Prompt and context were packaged deterministically; no LLM was used to invent architecture.");
        if (!context.included().isEmpty()) {
            notes.add("Included sections: " + String.join(", ", context.included().stream().limit(16).toList()));
        }
        notes.add("Context format: " + context.format().name().toLowerCase(Locale.ROOT));
        notes.add("Context id: " + context.id());
        return notes;
    }
}
