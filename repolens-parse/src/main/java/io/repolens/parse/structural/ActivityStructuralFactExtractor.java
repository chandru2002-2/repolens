package io.repolens.parse.structural;

import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified static activity facts for a method body (not a full CFG).
 */
public final class ActivityStructuralFactExtractor {

    private static final Pattern METHOD_CALL = Pattern.compile("\\b([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern IF_STMT = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern FOR_STMT = Pattern.compile("\\b(?:for|while)\\s*\\(");
    private static final Pattern RETURN_STMT = Pattern.compile("\\breturn\\b");
    private static final Set<String> CONTROL = Set.of(
            "if", "for", "while", "switch", "catch", "return", "new", "throw");

    private ActivityStructuralFactExtractor() {
    }

    public static void extract(
            StructuralFactSink sink,
            String path,
            String source,
            Symbol owner
    ) {
        if (owner == null || sink.factsFull()) {
            return;
        }
        List<Symbol> methods = sink.methodsByParent().getOrDefault(owner.id(), List.of()).stream()
                .filter(s -> s.kind() == SymbolKind.METHOD)
                .limit(6)
                .toList();
        for (Symbol method : methods) {
            if (sink.factsFull()) {
                return;
            }
            extractMethod(sink, path, source, method);
        }
    }

    private static void extractMethod(
            StructuralFactSink sink,
            String path,
            String source,
            Symbol method
    ) {
        int startLine = Math.max(1, method.location().startLine());
        int idx = source.indexOf(method.name() + "(");
        if (idx < 0) {
            return;
        }
        int brace = source.indexOf('{', idx);
        if (brace < 0) {
            return;
        }
        int end = StructuralFactSink.findMatchingBrace(source, brace);
        String body = end > brace ? source.substring(brace, end) : source.substring(brace);
        String scope = method.id();
        sink.add("activity", "start", "Start:" + method.name(),
                scope, null, "method=" + method.name(), path, startLine);
        int actions = 0;
        Matcher calls = METHOD_CALL.matcher(body);
        while (calls.find()
                && actions < StructuralFactSink.MAX_ACTIVITY_PER_METHOD
                && !sink.factsFull()) {
            String receiver = calls.group(1);
            String methodName = calls.group(2);
            if (CONTROL.contains(receiver)) {
                continue;
            }
            String label = receiver + "." + methodName + "()";
            sink.add("activity", "action", label, scope, null,
                    "method=" + method.name(), path,
                    startLine + StructuralFactSink.lineOf(body, calls.start()) - 1);
            actions++;
        }
        if (IF_STMT.matcher(body).find()) {
            sink.add("activity", "decision", "if?", scope, null,
                    "method=" + method.name(), path, startLine);
        }
        if (FOR_STMT.matcher(body).find()) {
            sink.add("activity", "loop", "loop", scope, null,
                    "method=" + method.name(), path, startLine);
        }
        sink.add("activity", "end", "End:" + method.name(), scope, null,
                "method=" + method.name(), path, startLine);
    }
}
