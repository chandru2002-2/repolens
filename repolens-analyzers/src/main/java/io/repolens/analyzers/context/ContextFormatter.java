package io.repolens.analyzers.context;

/**
 * Serializes a structured context draft into a user-facing document.
 */
public interface ContextFormatter {
    String format(ContextDraft draft);

    ContextFormat formatType();
}
