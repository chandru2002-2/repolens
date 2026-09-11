package io.repolens.analyzers.context;

/**
 * Preset AI tasks for Context Studio prompt generation.
 * Local/deterministic only — does not call an AI provider.
 */
public enum AiTaskPreset {
    EXPLAIN_ARCHITECTURE("Explain the architecture"),
    EXPLAIN_AUTHENTICATION_SECURITY("Explain authentication/security"),
    EXPLAIN_SELECTED_CLASS_SYMBOL("Explain a selected class/symbol"),
    TRACE_API_REQUEST("Trace an API request"),
    EXPLAIN_DATABASE_JPA("Explain database/JPA relationships"),
    EXPLAIN_DEPENDENCIES("Explain dependencies"),
    HELP_DEBUG_SELECTED_CODE("Help debug selected code"),
    IDENTIFY_ARCHITECTURAL_PROBLEMS("Identify potential architectural problems"),
    GENERATE_ONBOARDING_GUIDANCE("Generate onboarding guidance"),
    GENERATE_DOCUMENTATION("Generate documentation"),
    CUSTOM("Custom");

    private final String label;

    AiTaskPreset(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String defaultTaskText() {
        return switch (this) {
            case EXPLAIN_ARCHITECTURE ->
                    "Explain the architecture of this repository using only the provided context.";
            case EXPLAIN_AUTHENTICATION_SECURITY ->
                    "Explain how authentication and security work in this repository and trace relevant flows using only the provided context.";
            case EXPLAIN_SELECTED_CLASS_SYMBOL ->
                    "Explain the selected class or symbol, its role, and how it relates to neighboring code using only the provided context.";
            case TRACE_API_REQUEST ->
                    "Trace an API request through the layers and components described in the provided context.";
            case EXPLAIN_DATABASE_JPA ->
                    "Explain database and JPA relationships using only the entities, mappings, and facts in the provided context.";
            case EXPLAIN_DEPENDENCIES ->
                    "Explain the dependency structure among modules and components using only the provided context.";
            case HELP_DEBUG_SELECTED_CODE ->
                    "Help debug the selected code by reasoning from the provided repository context only.";
            case IDENTIFY_ARCHITECTURAL_PROBLEMS ->
                    "Identify potential architectural problems that are supported by the provided context. Do not invent issues without evidence.";
            case GENERATE_ONBOARDING_GUIDANCE ->
                    "Generate onboarding guidance for a new developer based only on the provided repository context.";
            case GENERATE_DOCUMENTATION ->
                    "Generate documentation drafts grounded only in the provided repository context.";
            case CUSTOM ->
                    "";
        };
    }

    /** Suggested Context Studio purpose for this task (UI may still override). */
    public ContextPurpose suggestedPurpose() {
        return switch (this) {
            case EXPLAIN_ARCHITECTURE, EXPLAIN_DEPENDENCIES, IDENTIFY_ARCHITECTURAL_PROBLEMS ->
                    ContextPurpose.ARCHITECTURE;
            case EXPLAIN_AUTHENTICATION_SECURITY -> ContextPurpose.SECURITY;
            case EXPLAIN_SELECTED_CLASS_SYMBOL, HELP_DEBUG_SELECTED_CODE ->
                    ContextPurpose.SELECTED_SYMBOLS;
            case TRACE_API_REQUEST -> ContextPurpose.API_BACKEND;
            case EXPLAIN_DATABASE_JPA -> ContextPurpose.DATABASE_JPA;
            case GENERATE_ONBOARDING_GUIDANCE, GENERATE_DOCUMENTATION ->
                    ContextPurpose.FULL_REPOSITORY;
            case CUSTOM -> ContextPurpose.CUSTOM;
        };
    }
}
