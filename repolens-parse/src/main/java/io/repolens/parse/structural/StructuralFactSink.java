package io.repolens.parse.structural;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.Relationship;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared mutable sink for structural-fact extraction: limits, type indexes, pending CALLS.
 */
public final class StructuralFactSink {

    public static final int MAX_FACTS = 2_000;
    public static final int MAX_CALLS = 400;
    public static final int MAX_ENDPOINTS = 400;
    public static final int MAX_TESTS = 400;
    public static final int MAX_ACTIVITY_PER_METHOD = 24;

    private final RepositoryModel.Builder builder;
    private final Map<String, Symbol> typeByName;
    private final Map<String, Symbol> typeByFile;
    private final Map<String, List<Symbol>> methodsByParent;
    private final Map<String, List<Symbol>> symbolsByFile;
    private final Set<String> seenCalls = new HashSet<>();
    private final Set<String> seenFactKeys = new HashSet<>();
    private final Set<String> seenEndpointKeys = new HashSet<>();
    private final Set<String> seenTestSymbols = new HashSet<>();
    private final List<Relationship> pendingCalls = new ArrayList<>();
    private int factSeq;
    private int endpointSeq;
    private int testSeq;

    public StructuralFactSink(RepositoryModel.Builder builder, List<Symbol> symbols) {
        this.builder = builder;
        this.typeByName = new HashMap<>();
        this.typeByFile = new HashMap<>();
        this.methodsByParent = new HashMap<>();
        this.symbolsByFile = new HashMap<>();
        for (Symbol symbol : symbols) {
            if (isType(symbol.kind())) {
                typeByName.putIfAbsent(symbol.name(), symbol);
                typeByFile.putIfAbsent(symbol.location().filePath(), symbol);
            }
            symbol.parentSymbolId().ifPresent(parent ->
                    methodsByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(symbol));
            symbolsByFile
                    .computeIfAbsent(symbol.location().filePath(), ignored -> new ArrayList<>())
                    .add(symbol);
        }
    }

    public Map<String, Symbol> typeByName() {
        return typeByName;
    }

    public Map<String, Symbol> typeByFile() {
        return typeByFile;
    }

    public Map<String, List<Symbol>> methodsByParent() {
        return methodsByParent;
    }

    public List<Symbol> symbolsInFile(String path) {
        return symbolsByFile.getOrDefault(path, List.of());
    }

    public boolean hasTestForSymbol(String symbolId) {
        return seenTestSymbols.contains(symbolId);
    }

    public List<Relationship> pendingCalls() {
        return pendingCalls;
    }

    public boolean factsFull() {
        return factSeq >= MAX_FACTS;
    }

    public boolean endpointsFull() {
        return endpointSeq >= MAX_ENDPOINTS;
    }

    public void addEndpoint(Endpoint endpoint) {
        if (endpoint == null || endpointSeq >= MAX_ENDPOINTS) {
            return;
        }
        String dedupe = endpoint.httpMethod() + "|" + endpoint.path() + "|"
                + endpoint.ownerTypeId().orElse("") + "|"
                + endpoint.handlerMethodId().orElse("") + "|"
                + endpoint.location().filePath() + "|"
                + endpoint.location().startLine();
        if (!seenEndpointKeys.add(dedupe)) {
            return;
        }
        builder.addEndpoint(new Endpoint(
                "endpoint:" + (++endpointSeq),
                endpoint.httpMethod(),
                endpoint.path(),
                endpoint.ownerTypeId(),
                endpoint.handlerMethodId(),
                endpoint.location(),
                endpoint.evidence()
        ));
    }

    public boolean testsFull() {
        return testSeq >= MAX_TESTS;
    }

    public void addTest(Test test) {
        if (test == null || testSeq >= MAX_TESTS) {
            return;
        }
        if (!seenTestSymbols.add(test.symbolId())) {
            return;
        }
        builder.addTest(new Test(
                "test:" + (++testSeq),
                test.symbolId(),
                test.frameworkHint(),
                test.location(),
                test.evidence()
        ));
    }

    public boolean callsFull() {
        return pendingCalls.size() >= MAX_CALLS;
    }

    public boolean noteCall(String key) {
        return seenCalls.add(key);
    }

    public void addCall(Relationship relationship) {
        if (pendingCalls.size() < MAX_CALLS) {
            pendingCalls.add(relationship);
        }
    }

    public void flushCalls() {
        for (Relationship call : pendingCalls) {
            builder.addRelationship(call);
        }
    }

    public int add(
            String category,
            String kind,
            String label,
            String sourceEntityId,
            String targetEntityId,
            String detail,
            String filePath,
            Integer line
    ) {
        if (factSeq >= MAX_FACTS || label == null || label.isBlank()) {
            return factSeq;
        }
        String dedupe = category + "|" + kind + "|" + label + "|"
                + nullToEmpty(sourceEntityId) + "|" + nullToEmpty(targetEntityId) + "|"
                + nullToEmpty(detail) + "|" + nullToEmpty(filePath);
        if (!seenFactKeys.add(dedupe)) {
            return factSeq;
        }
        builder.addStructuralFact(new StructuralFact(
                "fact:" + (++factSeq),
                category,
                kind,
                label,
                Optional.ofNullable(sourceEntityId),
                Optional.ofNullable(targetEntityId),
                Optional.ofNullable(detail),
                Optional.ofNullable(filePath),
                Optional.ofNullable(line)
        ));
        return factSeq;
    }

    public static int lineOf(String source, int offset) {
        int line = 1;
        int limit = Math.min(Math.max(offset, 0), source.length());
        for (int i = 0; i < limit; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    public static int findMatchingBrace(String source, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return source.length();
    }

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
