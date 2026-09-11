package io.repolens.parse.structural;

/**
 * Confidence semantics for statically inferred CALLS relationships.
 * Prefer omitting a relationship over emitting one below {@link #MIN_EMIT}.
 */
public final class CallConfidence {

    /** Directly resolved: static Type.method or field/ctor/setter-typed receiver. */
    public static final double HIGH = 0.90;

    /** Name/type heuristic (e.g. capitalized receiver matches a known type). */
    public static final double MEDIUM = 0.55;

    /** Role or weak heuristics — not emitted as CALLS by default. */
    public static final double LOW = 0.35;

    /** Minimum confidence required to emit a CALLS relationship or sequence call fact. */
    public static final double MIN_EMIT = 0.50;

    private CallConfidence() {
    }
}
