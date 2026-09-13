package io.repolens.analyzers;

import io.repolens.core.model.AnalysisResult;
import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Evidence;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Metric;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.Test;
import io.repolens.core.model.Trace;
import io.repolens.core.model.TraceHop;
import io.repolens.core.ports.Analyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Composes static traces from Endpoint facts and existing CALLS.
 * Does not parse source, invent convention hops, or write traces onto RepositoryModel.
 */
public final class TraceComposer implements Analyzer {

    /** Same threshold as parse-time CALLS emit ({@code CallConfidence.MIN_EMIT}). */
    public static final double MIN_CALL_CONFIDENCE = 0.50;
    public static final int MAX_DEPTH = 6;
    public static final int MAX_FANOUT = 4;
    public static final int MAX_TRACES_PER_ENDPOINT = 8;
    public static final int MAX_TRACES = 80;

    @Override
    public String id() {
        return "traces";
    }

    @Override
    public AnalysisResult analyze(RepositoryModel model) {
        List<Trace> traces = compose(model);
        List<Metric> metrics = List.of(
                new Metric(
                        "trace_count",
                        traces.size(),
                        Optional.empty(),
                        Optional.of(model.repository().id())
                )
        );
        return new AnalysisResult(
                id(),
                "Traces: " + traces.size() + " static paths",
                metrics,
                List.of(),
                List.of()
        );
    }

    public static List<Trace> compose(RepositoryModel model) {
        Set<String> testRelated = testRelatedIds(model);
        Map<String, List<Relationship>> callsByFrom = indexCalls(model);
        List<Endpoint> endpoints = model.endpoints().stream()
                .sorted(Comparator
                        .comparing(Endpoint::path)
                        .thenComparing(Endpoint::httpMethod)
                        .thenComparing(Endpoint::id))
                .toList();

        List<Trace> traces = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            if (traces.size() >= MAX_TRACES) {
                break;
            }
            if (endpoint.ownerTypeId().map(testRelated::contains).orElse(false)
                    || endpoint.handlerMethodId().map(testRelated::contains).orElse(false)) {
                continue;
            }
            List<Trace> forEndpoint = new ArrayList<>();
            List<TraceHop> prefix = new ArrayList<>();
            prefix.add(endpointHop(endpoint));
            Optional<String> start = seedHandlerOrOwner(endpoint, prefix);
            if (start.isEmpty()) {
                prefix.add(unresolvedHop(endpoint));
                forEndpoint.add(toTrace("pending", endpoint, prefix));
            } else {
                walk(model, callsByFrom, testRelated, endpoint, prefix, start.get(), 0, forEndpoint);
            }
            forEndpoint.sort(traceOrder());
            if (forEndpoint.size() > MAX_TRACES_PER_ENDPOINT) {
                forEndpoint = new ArrayList<>(forEndpoint.subList(0, MAX_TRACES_PER_ENDPOINT));
            }
            traces.addAll(forEndpoint);
        }
        traces.sort(traceOrder());
        if (traces.size() > MAX_TRACES) {
            traces = new ArrayList<>(traces.subList(0, MAX_TRACES));
        }
        List<Trace> numbered = new ArrayList<>(traces.size());
        int seq = 0;
        for (Trace trace : traces) {
            numbered.add(new Trace(
                    "trace:" + (++seq),
                    trace.endpointId(),
                    trace.hops(),
                    trace.confidence(),
                    trace.unresolved(),
                    Trace.STATIC
            ));
        }
        return numbered;
    }

    private static void walk(
            RepositoryModel model,
            Map<String, List<Relationship>> callsByFrom,
            Set<String> testRelated,
            Endpoint endpoint,
            List<TraceHop> prefix,
            String currentId,
            int depth,
            List<Trace> out
    ) {
        if (out.size() >= MAX_TRACES_PER_ENDPOINT) {
            return;
        }
        List<Relationship> outgoing = outgoingCalls(model, callsByFrom, currentId, testRelated, prefix);
        if (outgoing.isEmpty()) {
            List<TraceHop> hops = prefix;
            if (depth == 0) {
                hops = new ArrayList<>(prefix);
                hops.add(unresolvedHop(endpoint));
            }
            out.add(toTrace("pending", endpoint, hops));
            return;
        }
        if (depth >= MAX_DEPTH) {
            out.add(toTrace("pending", endpoint, prefix));
            return;
        }
        int branches = 0;
        for (Relationship call : outgoing) {
            if (branches >= MAX_FANOUT || out.size() >= MAX_TRACES_PER_ENDPOINT) {
                break;
            }
            branches++;
            List<TraceHop> next = new ArrayList<>(prefix);
            next.add(callHop(call));
            walk(model, callsByFrom, testRelated, endpoint, next, call.toId(), depth + 1, out);
        }
    }

    private static List<Relationship> outgoingCalls(
            RepositoryModel model,
            Map<String, List<Relationship>> callsByFrom,
            String currentId,
            Set<String> testRelated,
            List<TraceHop> prefix
    ) {
        Set<String> origins = new HashSet<>();
        origins.add(currentId);
        model.findSymbol(currentId).flatMap(Symbol::parentSymbolId).ifPresent(origins::add);
        Map<String, Relationship> bestByTarget = new HashMap<>();
        for (String origin : origins) {
            for (Relationship call : callsByFrom.getOrDefault(origin, List.of())) {
                if (call.confidence() < MIN_CALL_CONFIDENCE) {
                    continue;
                }
                if (testRelated.contains(call.toId())) {
                    continue;
                }
                if (inPath(prefix, call.toId())) {
                    continue;
                }
                Relationship existing = bestByTarget.get(call.toId());
                if (existing == null || call.confidence() > existing.confidence()
                        || (call.confidence() == existing.confidence()
                        && call.id().compareTo(existing.id()) < 0)) {
                    bestByTarget.put(call.toId(), call);
                }
            }
        }
        return bestByTarget.values().stream()
                .sorted(Comparator.comparing(Relationship::toId).thenComparing(Relationship::id))
                .limit(MAX_FANOUT)
                .toList();
    }

    private static boolean inPath(List<TraceHop> prefix, String entityId) {
        return prefix.stream().anyMatch(hop -> hop.entityId().equals(entityId));
    }

    private static Optional<String> seedHandlerOrOwner(Endpoint endpoint, List<TraceHop> prefix) {
        if (endpoint.handlerMethodId().isPresent()) {
            prefix.add(handlerHop(endpoint));
            return endpoint.handlerMethodId();
        }
        if (endpoint.ownerTypeId().isPresent()) {
            prefix.add(new TraceHop(
                    endpoint.ownerTypeId().get(),
                    TraceHop.ROLE_SYMBOL,
                    Optional.empty(),
                    endpoint.evidence(),
                    1.0,
                    true
            ));
            return endpoint.ownerTypeId();
        }
        return Optional.empty();
    }

    private static TraceHop endpointHop(Endpoint endpoint) {
        return new TraceHop(
                endpoint.id(),
                TraceHop.ROLE_ENDPOINT,
                Optional.empty(),
                endpoint.evidence(),
                1.0,
                true
        );
    }

    private static TraceHop handlerHop(Endpoint endpoint) {
        return new TraceHop(
                endpoint.handlerMethodId().orElseThrow(),
                TraceHop.ROLE_HANDLER,
                Optional.empty(),
                endpoint.evidence(),
                1.0,
                true
        );
    }

    private static TraceHop callHop(Relationship call) {
        Evidence evidence = call.evidence().orElseGet(() -> new Evidence(
                inferenceForCall(call),
                Optional.empty(),
                Optional.of(call.id()),
                call.provenance()
        ));
        return new TraceHop(
                call.toId(),
                TraceHop.ROLE_SYMBOL,
                Optional.of(call.id()),
                evidence,
                call.confidence(),
                true
        );
    }

    private static TraceHop unresolvedHop(Endpoint endpoint) {
        return new TraceHop(
                TraceHop.UNRESOLVED_ID,
                TraceHop.ROLE_UNRESOLVED,
                Optional.empty(),
                new Evidence(
                        InferenceMethod.NAME_HEURISTIC,
                        endpoint.evidence().location(),
                        Optional.of(endpoint.id()),
                        Optional.of("no CALLS above emit threshold")
                ),
                0.0,
                false
        );
    }

    private static InferenceMethod inferenceForCall(Relationship call) {
        String provenance = call.provenance().orElse("");
        if (provenance.contains("via=name-heuristic")) {
            return InferenceMethod.NAME_HEURISTIC;
        }
        if (call.confidence() >= 0.9) {
            return InferenceMethod.TYPE_RESOLUTION;
        }
        return InferenceMethod.TYPE_RESOLUTION;
    }

    private static Trace toTrace(String id, Endpoint endpoint, List<TraceHop> hops) {
        boolean unresolved = hops.stream().anyMatch(hop -> !hop.resolved());
        double confidence = hops.stream()
                .filter(TraceHop::resolved)
                .mapToDouble(TraceHop::confidence)
                .min()
                .orElse(0.0);
        return new Trace(id, endpoint.id(), hops, confidence, unresolved, Trace.STATIC);
    }

    private static Comparator<Trace> traceOrder() {
        return Comparator
                .comparing(Trace::endpointId)
                .thenComparing((Trace trace) -> hopSignature(trace.hops()))
                .thenComparing(Comparator.comparingInt((Trace trace) -> trace.hops().size()).reversed())
                .thenComparing(Comparator.comparingDouble(Trace::confidence).reversed());
    }

    private static String hopSignature(List<TraceHop> hops) {
        StringBuilder signature = new StringBuilder();
        for (TraceHop hop : hops) {
            signature.append(hop.role()).append(':').append(hop.entityId()).append('/');
        }
        return signature.toString();
    }

    private static Map<String, List<Relationship>> indexCalls(RepositoryModel model) {
        Map<String, List<Relationship>> byFrom = new HashMap<>();
        for (Relationship relationship : model.relationships()) {
            if (relationship.type() != RelationshipType.CALLS) {
                continue;
            }
            byFrom.computeIfAbsent(relationship.fromId(), ignored -> new ArrayList<>()).add(relationship);
        }
        return byFrom;
    }

    private static Set<String> testRelatedIds(RepositoryModel model) {
        Set<String> ids = new HashSet<>();
        for (Test test : model.tests()) {
            ids.add(test.symbolId());
        }
        for (Symbol symbol : model.symbols()) {
            Optional<String> parent = symbol.parentSymbolId();
            if (parent.isPresent() && ids.contains(parent.get())) {
                ids.add(symbol.id());
            }
        }
        return ids;
    }
}
