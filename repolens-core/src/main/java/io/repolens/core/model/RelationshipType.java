package io.repolens.core.model;

/**
 * Typed edge between model entities.
 */
public enum RelationshipType {
    CONTAINS,
    IMPORTS,
    EXTENDS,
    IMPLEMENTS,
    DEPENDS_ON,
    CALLS
}
