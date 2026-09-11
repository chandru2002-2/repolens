package io.repolens.parse.structural;

import io.repolens.core.model.Symbol;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JPA / persistence entity and association facts for ER diagrams.
 */
public final class JpaStructuralFactExtractor {

    private static final Pattern ENTITY = Pattern.compile("@Entity\\b");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ID_FIELD = Pattern.compile(
            "@Id\\b[\\s\\S]{0,120}?(?:private|protected|public)\\s+[\\w.<>,\\[\\]\\s]+\\s+(\\w+)\\s*[;=]",
            Pattern.MULTILINE);
    private static final Set<String> COLLECTION_TYPES = Set.of(
            "List", "Set", "Collection", "Map", "Optional", "Iterable", "Queue", "Deque"
    );
    private static final Pattern REL_BLOCK = Pattern.compile(
            "@(OneToMany|ManyToOne|OneToOne|ManyToMany)\\b([\\s\\S]{0,400}?)(?:private|protected|public)\\s+"
                    + "(?:static\\s+)?(?:final\\s+)?"
                    + "([\\w.]+(?:\\s*<\\s*[^>]+>)?)\\s+(\\w+)\\s*[;=]",
            Pattern.MULTILINE);
    private static final Pattern MAPPED_BY = Pattern.compile("mappedBy\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern JOIN_COLUMN = Pattern.compile("@JoinColumn\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

    private JpaStructuralFactExtractor() {
    }

    public static void extract(StructuralFactSink sink, String path, String source, Symbol owner) {
        if (!ENTITY.matcher(source).find() || sink.factsFull()) {
            return;
        }
        String ownerId = owner == null ? null : owner.id();
        String ownerName = owner == null ? pathName(path) : owner.name();
        String table = matchFirst(TABLE, source).orElse(ownerName);
        sink.add("er", "entity", ownerName, ownerId, null, "table=" + table, path, 1);

        Matcher idMatcher = ID_FIELD.matcher(source);
        if (idMatcher.find()) {
            sink.add("er", "primary_key", idMatcher.group(1), ownerId, null,
                    "pk", path, StructuralFactSink.lineOf(source, idMatcher.start()));
        }

        Matcher rel = REL_BLOCK.matcher(source);
        while (rel.find() && !sink.factsFull()) {
            String annotation = rel.group(1);
            String attrs = rel.group(2) == null ? "" : rel.group(2);
            String rawType = rel.group(3);
            String fieldName = rel.group(4);
            String targetName = elementType(rawType);
            if (isCollectionType(targetName)) {
                targetName = inferEntityFromFieldName(fieldName, sink);
            }
            if (targetName == null || targetName.isBlank() || isCollectionType(targetName)) {
                continue;
            }
            Symbol target = sink.typeByName().get(targetName);
            String cardinality = toCardinality(annotation);
            String mappedBy = matchFirst(MAPPED_BY, attrs).orElse(null);
            String joinWindow = source.substring(rel.start(), Math.min(source.length(), rel.end() + 80));
            String joinColumn = matchFirst(JOIN_COLUMN, attrs)
                    .or(() -> matchFirst(JOIN_COLUMN, joinWindow))
                    .orElse(null);
            String detail = cardinality
                    + (mappedBy != null ? ";mappedBy=" + mappedBy : "")
                    + (joinColumn != null ? ";joinColumn=" + joinColumn : "");
            sink.add("er", cardinality, ownerName + "->" + targetName,
                    ownerId, target == null ? null : target.id(),
                    detail, path, StructuralFactSink.lineOf(source, rel.start()));
        }
    }

    private static String elementType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String cleaned = rawType.trim();
        int lt = cleaned.indexOf('<');
        int gt = cleaned.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            cleaned = cleaned.substring(lt + 1, gt).trim();
            // Take first type arg for Map/List
            int comma = cleaned.indexOf(',');
            if (comma >= 0) {
                cleaned = cleaned.substring(0, comma).trim();
            }
        }
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) {
            cleaned = cleaned.substring(dot + 1);
        }
        return cleaned;
    }

    private static boolean isCollectionType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        String simple = typeName;
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) {
            simple = simple.substring(dot + 1);
        }
        return COLLECTION_TYPES.contains(simple);
    }

    /**
     * When {@code List pets} has no generic argument, use the field name only if a
     * matching {@code @Entity} type already exists (pets → Pet).
     */
    private static String inferEntityFromFieldName(String fieldName, StructuralFactSink sink) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String stem = fieldName;
        if (stem.endsWith("ies") && stem.length() > 3) {
            stem = stem.substring(0, stem.length() - 3) + "y";
        } else if (stem.endsWith("ses") && stem.length() > 3) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.endsWith("s") && stem.length() > 1 && !stem.endsWith("ss")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        String candidate = Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
        if (sink.typeByName().containsKey(candidate)) {
            return candidate;
        }
        String upper = fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1);
        if (sink.typeByName().containsKey(upper)) {
            return upper;
        }
        return null;
    }

    private static String toCardinality(String annotation) {
        return switch (annotation) {
            case "OneToMany" -> "one_to_many";
            case "ManyToOne" -> "many_to_one";
            case "OneToOne" -> "one_to_one";
            case "ManyToMany" -> "many_to_many";
            default -> "association";
        };
    }

    private static Optional<String> matchFirst(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String pathName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
