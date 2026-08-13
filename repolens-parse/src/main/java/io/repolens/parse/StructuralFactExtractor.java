package io.repolens.parse;

import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.StructuralFact;
import io.repolens.core.model.Symbol;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic structural-fact extraction for specialized diagrams.
 * Reads inventoried source/config only — no LLM, no invented relationships.
 */
public final class StructuralFactExtractor {

    private static final int MAX_FACTS = 2_000;
    private static final int MAX_CALLS = 400;
    private static final int MAX_ACTIVITY_PER_METHOD = 24;

    private static final Pattern ENTITY = Pattern.compile("@Entity\\b");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ID_FIELD = Pattern.compile(
            "@Id\\b[\\s\\S]{0,120}?(?:private|protected|public)\\s+[\\w.<>,\\[\\]\\s]+\\s+(\\w+)\\s*[;=]",
            Pattern.MULTILINE);
    private static final Pattern ONE_TO_MANY = Pattern.compile("@OneToMany\\b");
    private static final Pattern MANY_TO_ONE = Pattern.compile("@ManyToOne\\b");
    private static final Pattern ONE_TO_ONE = Pattern.compile("@OneToOne\\b");
    private static final Pattern MANY_TO_MANY = Pattern.compile("@ManyToMany\\b");
    private static final Pattern REL_TARGET = Pattern.compile(
            "@(?:OneToMany|ManyToOne|OneToOne|ManyToMany)\\b[\\s\\S]{0,200}?(?:private|protected|public)\\s+(?:List|Set|Collection)?\\s*<?\\s*([A-Z]\\w*)",
            Pattern.MULTILINE);
    private static final Pattern REST_CONTROLLER = Pattern.compile("@(?:RestController|Controller)\\b");
    private static final Pattern REQUEST_MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");
    private static final Pattern SERVICE = Pattern.compile("@Service\\b");
    private static final Pattern REPOSITORY = Pattern.compile("@Repository\\b|(?:extends|implements)\\s+\\w*Repository\\b");
    private static final Pattern METHOD_CALL = Pattern.compile("\\b([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern IF_STMT = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern FOR_STMT = Pattern.compile("\\b(?:for|while)\\s*\\(");
    private static final Pattern RETURN_STMT = Pattern.compile("\\breturn\\b");
    private static final Pattern ENUM_DECL = Pattern.compile(
            "(?:public\\s+|protected\\s+|private\\s+)?enum\\s+(\\w+)\\s*\\{([^}]+)}",
            Pattern.DOTALL);
    private static final Pattern SWITCH_ENUM = Pattern.compile(
            "switch\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\{([^}]+)}",
            Pattern.DOTALL);
    private static final Pattern CASE_LABEL = Pattern.compile("case\\s+([A-Z_][A-Z0-9_]*)\\s*:");
    private static final Pattern DOCKER_SERVICE = Pattern.compile(
            "(?m)^\\s{2}([A-Za-z0-9_.-]+):\\s*$");
    private static final Pattern DOCKER_IMAGE = Pattern.compile("(?m)^\\s+image:\\s*[\"']?([^\"'\\s]+)");
    private static final Pattern DOCKERFILE_FROM = Pattern.compile("(?im)^FROM\\s+(\\S+)");
    private static final Pattern K8S_KIND = Pattern.compile("(?m)^kind:\\s*(\\w+)");
    private static final Pattern K8S_NAME = Pattern.compile("(?m)^\\s+name:\\s*[\"']?([^\"'\\s]+)");
    private static final Pattern DATASOURCE = Pattern.compile(
            "(?i)(?:jdbc:|datasource|spring\\.datasource\\.url)\\S*");

    private StructuralFactExtractor() {
    }

    public static void extract(
            RepositoryModel.Builder builder,
            Path workingTree,
            WorkingTreeInventory inventory,
            List<Symbol> symbols
    ) {
        Map<String, Symbol> typeByName = new HashMap<>();
        Map<String, Symbol> typeByFile = new HashMap<>();
        Map<String, List<Symbol>> methodsByParent = new HashMap<>();
        for (Symbol symbol : symbols) {
            if (isType(symbol.kind())) {
                typeByName.putIfAbsent(symbol.name(), symbol);
                typeByFile.putIfAbsent(symbol.location().filePath(), symbol);
            }
            symbol.parentSymbolId().ifPresent(parent ->
                    methodsByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(symbol));
        }

        int factSeq = 0;
        Set<String> seenCalls = new HashSet<>();
        List<Relationship> pendingCalls = new ArrayList<>();

        for (WorkingTreeInventory.InventoriedFile file : inventory.files()) {
            if (factSeq >= MAX_FACTS) {
                break;
            }
            String path = file.relativePath().replace('\\', '/');
            String lower = path.toLowerCase(Locale.ROOT);
            Path absolute = workingTree.resolve(file.relativePath());
            String source = read(absolute);
            if (source == null || source.isBlank()) {
                continue;
            }

            if (lower.endsWith(".java")) {
                factSeq = extractJava(
                        builder,
                        typeByName,
                        typeByFile,
                        methodsByParent,
                        pendingCalls,
                        seenCalls,
                        path,
                        source,
                        factSeq
                );
            } else if (isComposeFile(lower)) {
                factSeq = extractCompose(builder, path, source, factSeq);
            } else if (lower.endsWith("dockerfile") || Path.of(lower).getFileName().toString().equals("dockerfile")) {
                factSeq = extractDockerfile(builder, path, source, factSeq);
            } else if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
                factSeq = extractKubernetesOrYaml(builder, path, source, factSeq);
            }
        }

        for (Relationship call : pendingCalls) {
            builder.addRelationship(call);
        }
    }

    private static int extractJava(
            RepositoryModel.Builder builder,
            Map<String, Symbol> typeByName,
            Map<String, Symbol> typeByFile,
            Map<String, List<Symbol>> methodsByParent,
            List<Relationship> pendingCalls,
            Set<String> seenCalls,
            String path,
            String source,
            int factSeq
    ) {
        Symbol owner = typeByFile.get(path);
        String ownerId = owner == null ? null : owner.id();
        String ownerName = owner == null ? Path.of(path).getFileName().toString() : owner.name();

        boolean entity = ENTITY.matcher(source).find();
        if (entity) {
            String table = matchFirst(TABLE, source).orElse(ownerName);
            factSeq = add(builder, factSeq, "er", "entity", ownerName, ownerId, null,
                    "table=" + table, path, 1);
            Matcher idMatcher = ID_FIELD.matcher(source);
            if (idMatcher.find()) {
                factSeq = add(builder, factSeq, "er", "primary_key", idMatcher.group(1), ownerId, null,
                        "pk", path, lineOf(source, idMatcher.start()));
            }
            Matcher rel = REL_TARGET.matcher(source);
            while (rel.find() && factSeq < MAX_FACTS) {
                String targetName = rel.group(1);
                Symbol target = typeByName.get(targetName);
                String cardinality = detectCardinality(source, rel.start());
                factSeq = add(builder, factSeq, "er", cardinality, ownerName + "->" + targetName,
                        ownerId, target == null ? null : target.id(),
                        cardinality, path, lineOf(source, rel.start()));
            }
        }

        boolean rest = REST_CONTROLLER.matcher(source).find();
        boolean service = SERVICE.matcher(source).find() || ownerName.endsWith("Service");
        boolean repository = REPOSITORY.matcher(source).find() || ownerName.endsWith("Repository");
        if (rest) {
            factSeq = add(builder, factSeq, "sequence", "controller", ownerName, ownerId, null,
                    "role=controller", path, null);
            factSeq = add(builder, factSeq, "dfd", "process", ownerName, ownerId, null,
                    "boundary=api", path, null);
            Matcher mappings = REQUEST_MAPPING.matcher(source);
            while (mappings.find() && factSeq < MAX_FACTS) {
                String route = mappings.group(1);
                String useCase = humanizeRoute(route, ownerName);
                factSeq = add(builder, factSeq, "usecase", "use_case", useCase, ownerId, null,
                        "route=" + route, path, lineOf(source, mappings.start()));
                factSeq = add(builder, factSeq, "usecase", "actor_link", "User->" + useCase,
                        null, ownerId, "actor=User", path, lineOf(source, mappings.start()));
            }
        }
        if (service) {
            factSeq = add(builder, factSeq, "sequence", "service", ownerName, ownerId, null,
                    "role=service", path, null);
            factSeq = add(builder, factSeq, "dfd", "process", ownerName, ownerId, null,
                    "process=service", path, null);
        }
        if (repository) {
            factSeq = add(builder, factSeq, "sequence", "repository", ownerName, ownerId, null,
                    "role=repository", path, null);
            factSeq = add(builder, factSeq, "dfd", "data_store", ownerName, ownerId, null,
                    "store=repository", path, null);
        }

        // Method calls -> CALLS + sequence edges
        if (ownerId != null && pendingCalls.size() < MAX_CALLS) {
            Matcher calls = METHOD_CALL.matcher(source);
            while (calls.find() && pendingCalls.size() < MAX_CALLS) {
                String receiver = calls.group(1);
                String method = calls.group(2);
                if (Set.of("if", "for", "while", "switch", "catch", "return", "new", "this", "super")
                        .contains(receiver)) {
                    continue;
                }
                Symbol targetType = resolveReceiver(receiver, typeByName, source);
                if (targetType == null || targetType.id().equals(ownerId)) {
                    continue;
                }
                String key = ownerId + "->" + targetType.id() + "." + method;
                if (!seenCalls.add(key)) {
                    continue;
                }
                pendingCalls.add(new Relationship(
                        "call-rel-" + (pendingCalls.size() + 1),
                        RelationshipType.CALLS,
                        ownerId,
                        targetType.id(),
                        0.55,
                        Optional.of("call:" + method)
                ));
                factSeq = add(builder, factSeq, "sequence", "call",
                        ownerName + "." + method + "->" + targetType.name(),
                        ownerId, targetType.id(), method, path, lineOf(source, calls.start()));
                factSeq = add(builder, factSeq, "dfd", "data_flow",
                        ownerName + "->" + targetType.name(),
                        ownerId, targetType.id(), method, path, lineOf(source, calls.start()));
            }
        }

        // Activity: scoped to methods of this type
        if (ownerId != null) {
            List<Symbol> methods = methodsByParent.getOrDefault(ownerId, List.of()).stream()
                    .filter(s -> s.kind() == SymbolKind.METHOD)
                    .limit(6)
                    .toList();
            for (Symbol method : methods) {
                factSeq = extractActivity(builder, path, source, method, factSeq);
            }
        }

        // State machine from enums
        Matcher enums = ENUM_DECL.matcher(source);
        while (enums.find() && factSeq < MAX_FACTS) {
            String enumName = enums.group(1);
            Symbol enumSym = typeByName.getOrDefault(enumName, owner);
            String enumId = enumSym == null ? null : enumSym.id();
            String body = enums.group(2);
            List<String> constants = new ArrayList<>();
            for (String part : body.split(",")) {
                String token = part.trim().split("[\\s(;]", 2)[0].trim();
                if (token.matches("[A-Z][A-Z0-9_]*")) {
                    constants.add(token);
                    factSeq = add(builder, factSeq, "state", "state", token, enumId, null,
                            "enum=" + enumName, path, lineOf(source, enums.start()));
                }
            }
            // Linear fallback transitions only when switch cases observed
            Matcher switches = SWITCH_ENUM.matcher(source);
            Set<String> caseStates = new HashSet<>();
            while (switches.find()) {
                Matcher cases = CASE_LABEL.matcher(switches.group(2));
                while (cases.find()) {
                    caseStates.add(cases.group(1));
                }
            }
            List<String> ordered = constants.stream().filter(caseStates::contains).toList();
            for (int i = 0; i + 1 < ordered.size() && factSeq < MAX_FACTS; i++) {
                factSeq = add(builder, factSeq, "state", "transition",
                        ordered.get(i) + "->" + ordered.get(i + 1),
                        enumId, null, "inferred=switch-order", path, null);
            }
        }

        return factSeq;
    }

    private static int extractActivity(
            RepositoryModel.Builder builder,
            String path,
            String source,
            Symbol method,
            int factSeq
    ) {
        int startLine = Math.max(1, method.location().startLine());
        // Approximate method body by scanning from method name occurrence
        int idx = source.indexOf(method.name() + "(");
        if (idx < 0) {
            return factSeq;
        }
        int brace = source.indexOf('{', idx);
        if (brace < 0) {
            return factSeq;
        }
        int end = findMatchingBrace(source, brace);
        String body = end > brace ? source.substring(brace, end) : source.substring(brace);
        String scope = method.id();
        factSeq = add(builder, factSeq, "activity", "start", "Start:" + method.name(),
                scope, null, "method=" + method.name(), path, startLine);
        int actions = 0;
        Matcher calls = METHOD_CALL.matcher(body);
        while (calls.find() && actions < MAX_ACTIVITY_PER_METHOD && factSeq < MAX_FACTS) {
            String label = calls.group(1) + "." + calls.group(2) + "()";
            factSeq = add(builder, factSeq, "activity", "action", label, scope, null,
                    "method=" + method.name(), path, startLine + lineOf(body, calls.start()) - 1);
            actions++;
        }
        if (IF_STMT.matcher(body).find()) {
            factSeq = add(builder, factSeq, "activity", "decision", "if?", scope, null,
                    "method=" + method.name(), path, startLine);
        }
        if (FOR_STMT.matcher(body).find()) {
            factSeq = add(builder, factSeq, "activity", "loop", "loop", scope, null,
                    "method=" + method.name(), path, startLine);
        }
        if (RETURN_STMT.matcher(body).find()) {
            factSeq = add(builder, factSeq, "activity", "end", "End:" + method.name(), scope, null,
                    "method=" + method.name(), path, startLine);
        } else {
            factSeq = add(builder, factSeq, "activity", "end", "End:" + method.name(), scope, null,
                    "method=" + method.name(), path, startLine);
        }
        return factSeq;
    }

    private static int extractCompose(RepositoryModel.Builder builder, String path, String source, int factSeq) {
        Matcher services = DOCKER_SERVICE.matcher(source);
        List<String> names = new ArrayList<>();
        while (services.find() && factSeq < MAX_FACTS) {
            String name = services.group(1);
            if (Set.of("services", "volumes", "networks", "version", "configs", "secrets").contains(name)) {
                continue;
            }
            names.add(name);
            String kind = inferDeployKind(name, source);
            factSeq = add(builder, factSeq, "deployment", kind, name, null, null,
                    "source=compose", path, lineOf(source, services.start()));
        }
        Matcher images = DOCKER_IMAGE.matcher(source);
        while (images.find() && factSeq < MAX_FACTS) {
            factSeq = add(builder, factSeq, "deployment", "image", images.group(1), null, null,
                    "source=compose-image", path, lineOf(source, images.start()));
        }
        for (int i = 0; i + 1 < names.size() && factSeq < MAX_FACTS; i++) {
            factSeq = add(builder, factSeq, "deployment", "links", names.get(i) + "->" + names.get(i + 1),
                    null, null, "inferred=compose-order", path, null);
        }
        return factSeq;
    }

    private static int extractDockerfile(RepositoryModel.Builder builder, String path, String source, int factSeq) {
        Matcher from = DOCKERFILE_FROM.matcher(source);
        while (from.find() && factSeq < MAX_FACTS) {
            factSeq = add(builder, factSeq, "deployment", "container", from.group(1), null, null,
                    "source=dockerfile", path, lineOf(source, from.start()));
        }
        return factSeq;
    }

    private static int extractKubernetesOrYaml(
            RepositoryModel.Builder builder,
            String path,
            String source,
            int factSeq
    ) {
        if (source.contains("kind:")) {
            Matcher kind = K8S_KIND.matcher(source);
            Matcher name = K8S_NAME.matcher(source);
            String kindName = kind.find() ? kind.group(1) : "Resource";
            String resourceName = name.find() ? name.group(1) : kindName;
            factSeq = add(builder, factSeq, "deployment", "k8s_" + kindName.toLowerCase(Locale.ROOT),
                    resourceName, null, null, "kind=" + kindName, path, null);
        }
        return extractDatasourceHint(builder, path, source, factSeq);
    }

    private static int extractDatasourceHint(
            RepositoryModel.Builder builder,
            String path,
            String source,
            int factSeq
    ) {
        if (DATASOURCE.matcher(source).find()) {
            factSeq = add(builder, factSeq, "deployment", "database", "Datasource", null, null,
                    "source=config", path, null);
            factSeq = add(builder, factSeq, "dfd", "data_store", "Database", null, null,
                    "source=config", path, null);
            factSeq = add(builder, factSeq, "sequence", "database", "Database", null, null,
                    "source=config", path, null);
        }
        return factSeq;
    }

    private static Symbol resolveReceiver(String receiver, Map<String, Symbol> typeByName, String source) {
        if (typeByName.containsKey(receiver)) {
            return typeByName.get(receiver);
        }
        // field type hint: Type name;
        Pattern fieldType = Pattern.compile(
                "(?:private|protected|public)\\s+([A-Z]\\w*)\\s+" + Pattern.quote(receiver) + "\\s*[;=]");
        Matcher matcher = fieldType.matcher(source);
        if (matcher.find()) {
            return typeByName.get(matcher.group(1));
        }
        String capitalized = Character.toUpperCase(receiver.charAt(0)) + receiver.substring(1);
        return typeByName.get(capitalized);
    }

    private static String detectCardinality(String source, int at) {
        String window = source.substring(Math.max(0, at - 40), Math.min(source.length(), at + 40));
        if (ONE_TO_MANY.matcher(window).find()) {
            return "one_to_many";
        }
        if (MANY_TO_ONE.matcher(window).find()) {
            return "many_to_one";
        }
        if (ONE_TO_ONE.matcher(window).find()) {
            return "one_to_one";
        }
        if (MANY_TO_MANY.matcher(window).find()) {
            return "many_to_many";
        }
        return "association";
    }

    private static String inferDeployKind(String name, String source) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("redis") || lower.contains("cache")) {
            return "cache";
        }
        if (lower.contains("postgres") || lower.contains("mysql") || lower.contains("mongo")
                || lower.contains("db") || lower.contains("database")) {
            return "database";
        }
        if (lower.contains("kafka") || lower.contains("rabbit") || lower.contains("queue")) {
            return "message_broker";
        }
        if (source.toLowerCase(Locale.ROOT).contains(name) && source.contains("ports:")) {
            return "service";
        }
        return "service";
    }

    private static String humanizeRoute(String route, String ownerName) {
        if (route == null || route.isBlank() || "/".equals(route)) {
            return ownerName.replace("Controller", "");
        }
        String cleaned = route.replaceAll("[{}]", "").replaceAll("^/+|/+$", "");
        if (cleaned.isBlank()) {
            return ownerName.replace("Controller", "");
        }
        String[] parts = cleaned.split("/");
        String last = parts[parts.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    private static int add(
            RepositoryModel.Builder builder,
            int factSeq,
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

    private static boolean isType(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.TYPE;
    }

    private static boolean isComposeFile(String lower) {
        String name = Path.of(lower).getFileName().toString();
        return name.equals("docker-compose.yml")
                || name.equals("docker-compose.yaml")
                || name.equals("compose.yml")
                || name.equals("compose.yaml");
    }

    private static Optional<String> matchFirst(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        int limit = Math.min(Math.max(offset, 0), source.length());
        for (int i = 0; i < limit; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int findMatchingBrace(String source, int openIdx) {
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

    private static String read(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }
}
