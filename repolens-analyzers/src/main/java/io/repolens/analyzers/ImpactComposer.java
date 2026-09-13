package io.repolens.analyzers;

import io.repolens.core.model.Evidence;
import io.repolens.core.model.Impact;
import io.repolens.core.model.ImpactItem;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Trace;
import io.repolens.core.model.TraceHop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reverse-indexes existing relationships and completed traces for a selected entity.
 * Does not parse source, write impact onto {@link RepositoryModel}, or invent naming-only links.
 */
public final class ImpactComposer {

    /** Same threshold as parse-time CALLS emit and {@link TraceComposer}. */
    public static final double MIN_CALL_CONFIDENCE = TraceComposer.MIN_CALL_CONFIDENCE;
    public static final int MAX_PER_CATEGORY = 40;

    private static final Set<RelationshipType> DEPENDENT_TYPES = EnumSet.of(
            RelationshipType.DEPENDS_ON,
            RelationshipType.IMPORTS,
            RelationshipType.EXTENDS,
            RelationshipType.IMPLEMENTS
    );

    private static final Comparator<ImpactItem> ITEM_ORDER = Comparator
            .comparing(ImpactItem::entityId)
            .thenComparing(item -> item.relationshipId().orElse(""))
            .thenComparing(item -> item.traceId().orElse(""))
            .thenComparing(ImpactItem::category);

    private ImpactComposer() {
    }

    public static Impact compose(RepositoryModel model, String entityId) {
        return compose(model, entityId, TraceComposer.compose(model));
    }

    public static Impact compose(RepositoryModel model, String entityId, List<Trace> traces) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(traces, "traces");
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }

        List<Relationship> graph = mergeRelationships(model);
        List<ImpactItem> callers = cap(directIncoming(graph, entityId, Set.of(RelationshipType.CALLS), true,
                ImpactItem.CATEGORY_CALLER, false));
        List<ImpactItem> dependents = cap(directIncoming(graph, entityId, DEPENDENT_TYPES, false,
                ImpactItem.CATEGORY_DEPENDENT, false));
        List<ImpactItem> tests = cap(directIncoming(graph, entityId, Set.of(RelationshipType.TESTS), false,
                ImpactItem.CATEGORY_TEST, false));
        List<ImpactItem> endpoints = cap(endpointsFromTraces(traces, entityId));
        List<ImpactItem> inferred = cap(inferredImpact(graph, entityId, callers, tests));

        return new Impact(entityId, callers, dependents, tests, endpoints, inferred);
    }

    private static List<Relationship> mergeRelationships(RepositoryModel model) {
        Map<String, Relationship> byKey = new HashMap<>();
        for (Relationship relationship : model.relationships()) {
            byKey.putIfAbsent(edgeKey(relationship), relationship);
        }
        for (Relationship relationship : new TestSubjectAnalyzer().analyze(model).relationships()) {
            byKey.putIfAbsent(edgeKey(relationship), relationship);
        }
        return new ArrayList<>(byKey.values());
    }

    private static String edgeKey(Relationship relationship) {
        return relationship.type() + "|" + relationship.fromId() + "|" + relationship.toId();
    }

    private static List<ImpactItem> directIncoming(
            List<Relationship> graph,
            String entityId,
            Set<RelationshipType> types,
            boolean applyCallThreshold,
            String category,
            boolean inferred
    ) {
        List<ImpactItem> items = new ArrayList<>();
        for (Relationship relationship : graph) {
            if (!types.contains(relationship.type()) || !relationship.toId().equals(entityId)) {
                continue;
            }
            if (applyCallThreshold && relationship.confidence() < MIN_CALL_CONFIDENCE) {
                continue;
            }
            items.add(fromRelationship(relationship, category, inferred));
        }
        items.sort(ITEM_ORDER);
        return items;
    }

    private static List<ImpactItem> endpointsFromTraces(List<Trace> traces, String entityId) {
        Map<String, ImpactItem> bestByEndpoint = new HashMap<>();
        for (Trace trace : traces) {
            Optional<TraceHop> hop = matchingHop(trace, entityId);
            if (hop.isEmpty()) {
                continue;
            }
            ImpactItem item = new ImpactItem(
                    trace.endpointId(),
                    ImpactItem.CATEGORY_ENDPOINT,
                    false,
                    hop.get().confidence(),
                    hop.get().evidence(),
                    hop.get().relationshipId(),
                    Optional.of(trace.id())
            );
            bestByEndpoint.merge(trace.endpointId(), item, ImpactComposer::prefer);
        }
        List<ImpactItem> items = new ArrayList<>(bestByEndpoint.values());
        items.sort(ITEM_ORDER);
        return items;
    }

    private static Optional<TraceHop> matchingHop(Trace trace, String entityId) {
        for (TraceHop hop : trace.hops()) {
            if (hop.entityId().equals(entityId)
                    || (trace.endpointId().equals(entityId) && hop.role().equals(TraceHop.ROLE_ENDPOINT))) {
                return Optional.of(hop);
            }
        }
        return Optional.empty();
    }

    private static List<ImpactItem> inferredImpact(
            List<Relationship> graph,
            String entityId,
            List<ImpactItem> directCallers,
            List<ImpactItem> directTests
    ) {
        Set<String> callerIds = new HashSet<>();
        for (ImpactItem caller : directCallers) {
            callerIds.add(caller.entityId());
        }
        Set<String> directCallerKeys = itemKeys(directCallers);
        Set<String> directTestKeys = itemKeys(directTests);
        Set<String> seen = new HashSet<>();
        List<ImpactItem> items = new ArrayList<>();

        for (Relationship first : graph) {
            if (first.type() != RelationshipType.CALLS
                    || !first.toId().equals(entityId)
                    || first.confidence() < MIN_CALL_CONFIDENCE) {
                continue;
            }
            for (Relationship second : graph) {
                if (second.type() != RelationshipType.CALLS
                        || !second.toId().equals(first.fromId())
                        || second.confidence() < MIN_CALL_CONFIDENCE) {
                    continue;
                }
                if (second.fromId().equals(entityId)) {
                    continue;
                }
                String key = "calls|" + second.fromId() + "|" + second.id() + "|" + first.id();
                if (!seen.add(key) || directCallerKeys.contains(second.fromId())) {
                    continue;
                }
                items.add(composed(
                        second.fromId(),
                        Math.min(first.confidence(), second.confidence()),
                        composeEvidence(second, first, "inferred caller via CALLS"),
                        second.id()
                ));
            }
        }

        for (Relationship tests : graph) {
            if (tests.type() != RelationshipType.TESTS || !callerIds.contains(tests.toId())) {
                continue;
            }
            if (tests.toId().equals(entityId) || directTestKeys.contains(tests.fromId())) {
                continue;
            }
            String key = "tests|" + tests.fromId() + "|" + tests.id();
            if (!seen.add(key)) {
                continue;
            }
            Optional<Relationship> call = incomingCall(graph, entityId, tests.toId());
            double confidence = call
                    .map(edge -> Math.min(tests.confidence(), edge.confidence()))
                    .orElse(tests.confidence());
            Evidence evidence = call
                    .map(edge -> composeEvidence(tests, edge, "inferred tests via caller CALLS"))
                    .orElseGet(() -> evidenceOf(tests, "inferred tests via caller"));
            items.add(composed(tests.fromId(), confidence, evidence, tests.id()));
        }

        items.sort(ITEM_ORDER);
        return items;
    }

    private static Optional<Relationship> incomingCall(List<Relationship> graph, String entityId, String fromId) {
        return graph.stream()
                .filter(rel -> rel.type() == RelationshipType.CALLS
                        && rel.fromId().equals(fromId)
                        && rel.toId().equals(entityId)
                        && rel.confidence() >= MIN_CALL_CONFIDENCE)
                .min(Comparator.comparing(Relationship::id));
    }

    private static ImpactItem composed(String entityId, double confidence, Evidence evidence, String relationshipId) {
        return new ImpactItem(
                entityId,
                ImpactItem.CATEGORY_INFERRED,
                true,
                confidence,
                evidence,
                Optional.of(relationshipId),
                Optional.empty()
        );
    }

    private static ImpactItem fromRelationship(Relationship relationship, String category, boolean inferred) {
        return new ImpactItem(
                relationship.fromId(),
                category,
                inferred,
                relationship.confidence(),
                evidenceOf(relationship, relationship.type().name()),
                Optional.of(relationship.id()),
                Optional.empty()
        );
    }

    private static Evidence evidenceOf(Relationship relationship, String summary) {
        return relationship.evidence().orElseGet(() -> new Evidence(
                inferenceMethod(relationship),
                Optional.empty(),
                Optional.of(relationship.id()),
                relationship.provenance().or(() -> Optional.of(summary))
        ));
    }

    private static Evidence composeEvidence(Relationship primary, Relationship support, String summary) {
        Evidence base = evidenceOf(primary, summary);
        return new Evidence(
                base.inferenceMethod(),
                base.location(),
                Optional.of(primary.id() + "+" + support.id()),
                Optional.of(summary)
        );
    }

    private static InferenceMethod inferenceMethod(Relationship relationship) {
        return relationship.evidence()
                .map(Evidence::inferenceMethod)
                .orElseGet(() -> {
                    String provenance = relationship.provenance().orElse("");
                    if (provenance.contains("via=name-heuristic")) {
                        return InferenceMethod.NAME_HEURISTIC;
                    }
                    return InferenceMethod.TYPE_RESOLUTION;
                });
    }

    private static Set<String> itemKeys(List<ImpactItem> items) {
        Set<String> keys = new HashSet<>();
        for (ImpactItem item : items) {
            keys.add(item.entityId());
        }
        return keys;
    }

    private static ImpactItem prefer(ImpactItem left, ImpactItem right) {
        int byConfidence = Double.compare(right.confidence(), left.confidence());
        if (byConfidence != 0) {
            return byConfidence > 0 ? right : left;
        }
        return ITEM_ORDER.compare(left, right) <= 0 ? left : right;
    }

    private static List<ImpactItem> cap(List<ImpactItem> items) {
        if (items.size() <= MAX_PER_CATEGORY) {
            return List.copyOf(items);
        }
        return List.copyOf(items.subList(0, MAX_PER_CATEGORY));
    }
}
