package io.repolens.analyzers.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic JSON serializer without requiring Jackson in analyzers.
 */
public final class JsonContextFormatter implements ContextFormatter {

    @Override
    public ContextFormat formatType() {
        return ContextFormat.JSON;
    }

    @Override
    public String format(ContextDraft draft) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", draft.title());
        root.put("purpose", draft.purpose().name());
        root.put("strategy", draft.strategy().name());
        root.put("strategyLabel", draft.strategy().displayName());
        root.put("scopeMode", draft.scope().mode().name());
        root.put("scope", Map.of(
                "filePaths", draft.scope().filePaths(),
                "symbolIds", draft.scope().symbolIds(),
                "graphNodeIds", draft.scope().graphNodeIds()
        ));
        root.put("tokenBudget", draft.tokenBudget());
        root.put("repository", draft.repository());
        root.put("modules", draft.modules());
        root.put("components", draft.components());
        root.put("relationships", draft.relationships());
        root.put("endpoints", draft.endpoints());
        root.put("dataModels", draft.dataModels());
        root.put("configuration", draft.configuration());
        root.put("documentation", draft.documentation());
        root.put("sourceSnippets", draft.sourceSnippets());
        root.put("analysis", draft.analysisNotes());
        root.put("included", draft.included());
        root.put("excluded", draft.excluded());
        return JsonWriter.write(root) + "\n";
    }

    /** Minimal JSON writer for maps/lists/scalars. */
    static final class JsonWriter {
        private JsonWriter() {
        }

        static String write(Object value) {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, value);
            return sb.toString();
        }

        private static void writeValue(StringBuilder sb, Object value) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s) {
                sb.append('"').append(escape(s)).append('"');
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof Map<?, ?> map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                    writeValue(sb, e.getValue());
                }
                sb.append('}');
            } else if (value instanceof Iterable<?> it) {
                sb.append('[');
                boolean first = true;
                for (Object item : it) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeValue(sb, item);
                }
                sb.append(']');
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
