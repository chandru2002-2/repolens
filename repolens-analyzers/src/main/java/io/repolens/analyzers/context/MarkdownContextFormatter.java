package io.repolens.analyzers.context;

import java.util.List;
import java.util.Map;

public final class MarkdownContextFormatter implements ContextFormatter {

    @Override
    public ContextFormat formatType() {
        return ContextFormat.MARKDOWN;
    }

    @Override
    public String format(ContextDraft draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Repository Context\n\n");
        sb.append("## Packaging Strategy\n").append(draft.strategy().displayName()).append("\n\n");
        sb.append("## Context Purpose\n").append(draft.purpose().name()).append("\n\n");
        sb.append("## Title\n").append(draft.title()).append("\n\n");
        sb.append("## Scope\n").append(draft.scope().mode().name());
        if (!draft.scope().symbolIds().isEmpty()) {
            sb.append(" · symbols=").append(draft.scope().symbolIds().size());
        }
        if (!draft.scope().filePaths().isEmpty()) {
            sb.append(" · files=").append(draft.scope().filePaths().size());
        }
        if (!draft.scope().graphNodeIds().isEmpty()) {
            sb.append(" · nodes=").append(draft.scope().graphNodeIds().size());
        }
        sb.append("\n\n");
        sb.append("## Token Budget\n")
                .append(draft.tokenBudget())
                .append(" (estimated tokens reported separately; approximation)\n\n");

        sectionMap(sb, "Repository", draft.repository());
        sectionList(sb, "Modules", draft.modules());
        sectionList(sb, "Key Components", draft.components());
        sectionList(sb, "Relationships", draft.relationships());
        sectionList(sb, "Endpoints", draft.endpoints());
        sectionList(sb, "Data Models", draft.dataModels());
        sectionList(sb, "Configuration", draft.configuration());
        sectionList(sb, "Documentation", draft.documentation());
        sectionList(sb, "Relevant Source Files", draft.sourceSnippets());
        sectionList(sb, "Analysis Notes", draft.analysisNotes());

        if (!draft.included().isEmpty()) {
            sb.append("## Included\n");
            for (String item : draft.included()) {
                sb.append("- ").append(item).append('\n');
            }
            sb.append('\n');
        }
        if (!draft.excluded().isEmpty()) {
            sb.append("## Excluded\n");
            for (String item : draft.excluded()) {
                sb.append("- ").append(item).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    private static void sectionMap(StringBuilder sb, String title, Map<String, String> map) {
        if (map.isEmpty()) {
            return;
        }
        sb.append("## ").append(title).append('\n');
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append('\n');
    }

    private static void sectionList(StringBuilder sb, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append("## ").append(title).append('\n');
        for (String item : items) {
            if (item.contains("\n")) {
                sb.append(item);
                if (!item.endsWith("\n")) {
                    sb.append('\n');
                }
                sb.append('\n');
            } else {
                sb.append("- ").append(item).append('\n');
            }
        }
        sb.append('\n');
    }
}
