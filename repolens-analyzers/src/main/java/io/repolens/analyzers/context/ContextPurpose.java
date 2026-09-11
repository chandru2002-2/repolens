package io.repolens.analyzers.context;

/**
 * Semantic purpose for a Context Studio generation request.
 */
public enum ContextPurpose {
    FULL_REPOSITORY,
    ARCHITECTURE,
    SELECTED_FILES,
    SELECTED_SYMBOLS,
    API_BACKEND,
    DATABASE_JPA,
    SECURITY,
    CUSTOM
}
