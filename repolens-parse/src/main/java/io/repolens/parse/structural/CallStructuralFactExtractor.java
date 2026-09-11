package io.repolens.parse.structural;

import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Symbol;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Method-call detection with typed receiver resolution and confidence.
 */
public final class CallStructuralFactExtractor {

    private static final Pattern METHOD_CALL = Pattern.compile(
            "\\b(?:(this|super)|([A-Za-z_]\\w*))\\.([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern FIELD_DECL = Pattern.compile(
            "(?:private|protected|public)\\s+(?:static\\s+)?(?:final\\s+)?"
                    + "([A-Z][\\w.]*(?:\\s*<[^;{]+>)?)\\s+([a-z]\\w*)\\s*[;=]",
            Pattern.MULTILINE);
    private static final Pattern CTOR_PARAM = Pattern.compile(
            "(?:public|protected|private)\\s+[A-Z]\\w*\\s*\\(([^)]*)\\)");
    private static final Pattern SETTER = Pattern.compile(
            "(?:public|protected)\\s+void\\s+set([A-Z]\\w*)\\s*\\(\\s*([A-Z][\\w.]*)\\s+\\w+\\s*\\)");
    private static final Set<String> CONTROL = Set.of(
            "if", "for", "while", "switch", "catch", "return", "new", "throw", "synchronized",
            "try", "else", "case", "default", "assert", "instanceof");

    private CallStructuralFactExtractor() {
    }

    public static void extract(
            StructuralFactSink sink,
            String path,
            String source,
            Symbol owner
    ) {
        if (owner == null || sink.callsFull()) {
            return;
        }
        String ownerId = owner.id();
        String ownerName = owner.name();
        Map<String, ResolvedType> locals = buildReceiverIndex(source, sink);

        Matcher calls = METHOD_CALL.matcher(source);
        while (calls.find() && !sink.callsFull() && !sink.factsFull()) {
            String thisOrSuper = calls.group(1);
            String receiver = calls.group(2);
            String method = calls.group(3);
            if (thisOrSuper != null) {
                // Self / superclass calls are not inter-type sequence edges.
                continue;
            }
            if (receiver == null || CONTROL.contains(receiver) || CONTROL.contains(method)) {
                continue;
            }
            ResolvedType resolved = resolve(receiver, locals, sink, source);
            if (resolved == null || resolved.symbol() == null) {
                continue;
            }
            Symbol targetType = resolved.symbol();
            if (targetType.id().equals(ownerId)) {
                continue;
            }
            if (resolved.confidence() < CallConfidence.MIN_EMIT) {
                continue;
            }
            String key = ownerId + "->" + targetType.id() + "." + method;
            if (!sink.noteCall(key)) {
                continue;
            }
            String provenance = "call:" + method + ";confidence="
                    + confidenceLabel(resolved.confidence())
                    + ";via=" + resolved.via();
            sink.addCall(new Relationship(
                    "call-rel-" + (sink.pendingCalls().size() + 1),
                    RelationshipType.CALLS,
                    ownerId,
                    targetType.id(),
                    resolved.confidence(),
                    Optional.of(provenance)
            ));
            sink.add("sequence", "call",
                    ownerName + "." + method + "->" + targetType.name(),
                    ownerId, targetType.id(), method, path,
                    StructuralFactSink.lineOf(source, calls.start()));
            sink.add("dfd", "data_flow",
                    ownerName + "->" + targetType.name(),
                    ownerId, targetType.id(), method, path,
                    StructuralFactSink.lineOf(source, calls.start()));
        }
    }

    private static Map<String, ResolvedType> buildReceiverIndex(String source, StructuralFactSink sink) {
        Map<String, ResolvedType> index = new HashMap<>();

        Matcher fields = FIELD_DECL.matcher(source);
        while (fields.find()) {
            String typeName = simpleType(fields.group(1));
            String fieldName = fields.group(2);
            Symbol type = sink.typeByName().get(typeName);
            if (type != null) {
                index.put(fieldName, new ResolvedType(type, CallConfidence.HIGH, "field"));
            }
        }

        Matcher ctors = CTOR_PARAM.matcher(source);
        while (ctors.find()) {
            String params = ctors.group(1);
            if (params == null || params.isBlank()) {
                continue;
            }
            for (String param : params.split(",")) {
                String trimmed = param.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                String[] parts = trimmed.replaceAll("<[^>]*>", " ").trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                String typeName = simpleType(parts[parts.length - 2]);
                String paramName = parts[parts.length - 1].replaceAll("\\W", "");
                Symbol type = sink.typeByName().get(typeName);
                if (type != null && !paramName.isBlank()) {
                    index.putIfAbsent(paramName, new ResolvedType(type, CallConfidence.HIGH, "constructor"));
                }
            }
        }

        Matcher setters = SETTER.matcher(source);
        while (setters.find()) {
            String fieldGuess = Character.toLowerCase(setters.group(1).charAt(0))
                    + setters.group(1).substring(1);
            String typeName = simpleType(setters.group(2));
            Symbol type = sink.typeByName().get(typeName);
            if (type != null) {
                index.putIfAbsent(fieldGuess, new ResolvedType(type, CallConfidence.HIGH, "setter"));
            }
        }
        return index;
    }

    private static ResolvedType resolve(
            String receiver,
            Map<String, ResolvedType> locals,
            StructuralFactSink sink,
            String source
    ) {
        if (locals.containsKey(receiver)) {
            return locals.get(receiver);
        }
        // Static call: TypeName.method(...)
        Symbol staticType = sink.typeByName().get(receiver);
        if (staticType != null && Character.isUpperCase(receiver.charAt(0))) {
            return new ResolvedType(staticType, CallConfidence.HIGH, "static");
        }
        // Field type pattern not captured earlier (nested generics edge cases)
        Pattern fieldType = Pattern.compile(
                "(?:private|protected|public)\\s+(?:static\\s+)?(?:final\\s+)?"
                        + "([A-Z]\\w*(?:\\s*<[^;{]+>)?)\\s+" + Pattern.quote(receiver) + "\\s*[;=]");
        Matcher matcher = fieldType.matcher(source);
        if (matcher.find()) {
            Symbol type = sink.typeByName().get(simpleType(matcher.group(1)));
            if (type != null) {
                return new ResolvedType(type, CallConfidence.HIGH, "field");
            }
        }
        String capitalized = Character.toUpperCase(receiver.charAt(0)) + receiver.substring(1);
        Symbol heuristic = sink.typeByName().get(capitalized);
        if (heuristic != null) {
            return new ResolvedType(heuristic, CallConfidence.MEDIUM, "name-heuristic");
        }
        return null;
    }

    private static String simpleType(String raw) {
        String cleaned = raw.trim();
        int generic = cleaned.indexOf('<');
        if (generic >= 0) {
            cleaned = cleaned.substring(0, generic).trim();
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) {
            cleaned = cleaned.substring(dot + 1);
        }
        return cleaned;
    }

    private static String confidenceLabel(double confidence) {
        if (confidence >= CallConfidence.HIGH - 0.01) {
            return "high";
        }
        if (confidence >= CallConfidence.MEDIUM - 0.01) {
            return "medium";
        }
        return "low";
    }

    private record ResolvedType(Symbol symbol, double confidence, String via) {
    }
}
