package io.repolens.core.model;

/**
 * How a fact or relationship was inferred. Framework-agnostic; not runtime proof.
 */
public enum InferenceMethod {
    AST_CAPTURE,
    ANNOTATION,
    TYPE_RESOLUTION,
    NAME_HEURISTIC,
    FRAMEWORK_CONVENTION
}
