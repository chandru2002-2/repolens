package io.repolens.parse.structural;

import io.repolens.core.model.Symbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State-machine facts from enums. Transitions require explicit assignment/return evidence —
 * never enum declaration order or switch-case order alone.
 */
public final class StateStructuralFactExtractor {

    private static final Pattern ENUM_DECL = Pattern.compile(
            "(?:public\\s+|protected\\s+|private\\s+)?enum\\s+(\\w+)\\s*\\{([^}]+)}",
            Pattern.DOTALL);
    private static final Pattern SWITCH_ENUM = Pattern.compile(
            "switch\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\{([^}]+)}",
            Pattern.DOTALL);
    private static final Pattern CASE_BLOCK = Pattern.compile(
            "case\\s+([A-Z_][A-Z0-9_]*)\\s*:(.*?)(?=case\\s+[A-Z_][A-Z0-9_]*\\s*:|default\\s*:|$)",
            Pattern.DOTALL);

    private StateStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source, Symbol owner) {
        Matcher enums = ENUM_DECL.matcher(source);
        while (enums.find() && !sink.factsFull()) {
            String enumName = enums.group(1);
            Symbol enumSym = sink.typeByName().getOrDefault(enumName, owner);
            String enumId = enumSym == null ? null : enumSym.id();
            String body = enums.group(2);
            Set<String> constants = new HashSet<>();
            for (String part : body.split(",")) {
                String token = part.trim().split("[\\s(;]", 2)[0].trim();
                if (token.matches("[A-Z][A-Z0-9_]*")) {
                    constants.add(token);
                    sink.add("state", "state", token, enumId, null,
                            "enum=" + enumName, path, StructuralFactSink.lineOf(source, enums.start()));
                }
            }
            emitEvidenceBasedTransitions(sink, path, source, enumName, enumId, constants);
        }
    }

    private static void emitEvidenceBasedTransitions(
            StructuralFactSink sink,
            String path,
            String source,
            String enumName,
            String enumId,
            Set<String> constants
    ) {
        // Explicit assignments / returns anywhere: EnumName.FROM ... EnumName.TO is weak;
        // prefer switch case FROM containing EnumName.TO assignment or return.
        Matcher switches = SWITCH_ENUM.matcher(source);
        while (switches.find() && !sink.factsFull()) {
            String switchBody = switches.group(2);
            Matcher cases = CASE_BLOCK.matcher(switchBody);
            while (cases.find() && !sink.factsFull()) {
                String from = cases.group(1);
                if (!constants.contains(from)) {
                    continue;
                }
                String caseBody = cases.group(2);
                for (String to : findTargetStates(caseBody, enumName, constants)) {
                    if (to.equals(from)) {
                        continue;
                    }
                    sink.add("state", "transition", from + "->" + to, enumId, null,
                            "evidence=switch-assign-or-return;enum=" + enumName,
                            path, StructuralFactSink.lineOf(source, switches.start()));
                }
            }
        }

        // Explicit transition-style calls: transitionTo(Status.X) / moveTo(Status.X)
        Pattern transitionCall = Pattern.compile(
                "(?:transitionTo|moveTo|setStatus|setState)\\s*\\(\\s*"
                        + Pattern.quote(enumName) + "\\.([A-Z_][A-Z0-9_]*)\\s*\\)");
        Matcher transitions = transitionCall.matcher(source);
        while (transitions.find() && !sink.factsFull()) {
            String to = transitions.group(1);
            if (!constants.contains(to)) {
                continue;
            }
            // Without a known from-state, do not invent a source — emit only annotated target hint.
            // Prefer pairing with nearby from=EnumName.FROM if present in same statement context.
            String window = source.substring(Math.max(0, transitions.start() - 120), transitions.start());
            Matcher fromRef = Pattern.compile(Pattern.quote(enumName) + "\\.([A-Z_][A-Z0-9_]*)").matcher(window);
            String from = null;
            while (fromRef.find()) {
                from = fromRef.group(1);
            }
            if (from != null && constants.contains(from) && !from.equals(to)) {
                sink.add("state", "transition", from + "->" + to, enumId, null,
                        "evidence=transition-call;enum=" + enumName,
                        path, StructuralFactSink.lineOf(source, transitions.start()));
            }
        }
    }

    private static List<String> findTargetStates(String caseBody, String enumName, Set<String> constants) {
        List<String> targets = new ArrayList<>();
        Pattern assignOrReturn = Pattern.compile(
                "(?:return|=)\\s*" + Pattern.quote(enumName) + "\\.([A-Z_][A-Z0-9_]*)");
        Matcher matcher = assignOrReturn.matcher(caseBody);
        while (matcher.find()) {
            String to = matcher.group(1);
            if (constants.contains(to) && !targets.contains(to)) {
                targets.add(to);
            }
        }
        // Bare assignment to status variable: status = COMPLETED when COMPLETED is enum const
        Pattern bareAssign = Pattern.compile("=\\s*([A-Z_][A-Z0-9_]*)\\s*;");
        Matcher bare = bareAssign.matcher(caseBody);
        while (bare.find()) {
            String to = bare.group(1);
            if (constants.contains(to) && !targets.contains(to)) {
                targets.add(to);
            }
        }
        return targets;
    }
}
