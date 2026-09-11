package io.repolens.analyzers.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Intermediate structured context before Markdown/JSON serialization.
 */
public final class ContextDraft {

    private final String title;
    private final ContextPurpose purpose;
    private final ContextScope scope;
    private final ContextStrategy strategy;
    private final int tokenBudget;
    private final Map<String, String> repository;
    private final List<String> modules;
    private final List<String> components;
    private final List<String> relationships;
    private final List<String> endpoints;
    private final List<String> dataModels;
    private final List<String> configuration;
    private final List<String> documentation;
    private final List<String> sourceSnippets;
    private final List<String> analysisNotes;
    private final List<String> included;
    private final List<String> excluded;

    private ContextDraft(Builder builder) {
        this.title = builder.title;
        this.purpose = builder.purpose;
        this.scope = builder.scope;
        this.strategy = builder.strategy;
        this.tokenBudget = builder.tokenBudget;
        this.repository = Map.copyOf(builder.repository);
        this.modules = List.copyOf(builder.modules);
        this.components = List.copyOf(builder.components);
        this.relationships = List.copyOf(builder.relationships);
        this.endpoints = List.copyOf(builder.endpoints);
        this.dataModels = List.copyOf(builder.dataModels);
        this.configuration = List.copyOf(builder.configuration);
        this.documentation = List.copyOf(builder.documentation);
        this.sourceSnippets = List.copyOf(builder.sourceSnippets);
        this.analysisNotes = List.copyOf(builder.analysisNotes);
        this.included = List.copyOf(builder.included);
        this.excluded = List.copyOf(builder.excluded);
    }

    public String title() {
        return title;
    }

    public ContextPurpose purpose() {
        return purpose;
    }

    public ContextScope scope() {
        return scope;
    }

    public ContextStrategy strategy() {
        return strategy;
    }

    public int tokenBudget() {
        return tokenBudget;
    }

    public Map<String, String> repository() {
        return repository;
    }

    public List<String> modules() {
        return modules;
    }

    public List<String> components() {
        return components;
    }

    public List<String> relationships() {
        return relationships;
    }

    public List<String> endpoints() {
        return endpoints;
    }

    public List<String> dataModels() {
        return dataModels;
    }

    public List<String> configuration() {
        return configuration;
    }

    public List<String> documentation() {
        return documentation;
    }

    public List<String> sourceSnippets() {
        return sourceSnippets;
    }

    public List<String> analysisNotes() {
        return analysisNotes;
    }

    public List<String> included() {
        return included;
    }

    public List<String> excluded() {
        return excluded;
    }

    public static Builder builder(
            String title,
            ContextPurpose purpose,
            ContextScope scope,
            ContextStrategy strategy,
            int tokenBudget
    ) {
        return new Builder(title, purpose, scope, strategy, tokenBudget);
    }

    public static final class Builder {
        private final String title;
        private final ContextPurpose purpose;
        private final ContextScope scope;
        private final ContextStrategy strategy;
        private final int tokenBudget;
        private final Map<String, String> repository = new LinkedHashMap<>();
        private final List<String> modules = new ArrayList<>();
        private final List<String> components = new ArrayList<>();
        private final List<String> relationships = new ArrayList<>();
        private final List<String> endpoints = new ArrayList<>();
        private final List<String> dataModels = new ArrayList<>();
        private final List<String> configuration = new ArrayList<>();
        private final List<String> documentation = new ArrayList<>();
        private final List<String> sourceSnippets = new ArrayList<>();
        private final List<String> analysisNotes = new ArrayList<>();
        private final List<String> included = new ArrayList<>();
        private final List<String> excluded = new ArrayList<>();

        private Builder(
                String title,
                ContextPurpose purpose,
                ContextScope scope,
                ContextStrategy strategy,
                int tokenBudget
        ) {
            this.title = Objects.requireNonNull(title);
            this.purpose = Objects.requireNonNull(purpose);
            this.scope = Objects.requireNonNull(scope);
            this.strategy = strategy == null ? ContextStrategy.ARCHITECTURE_OVERVIEW : strategy;
            this.tokenBudget = tokenBudget;
        }

        public Builder repositoryField(String key, String value) {
            if (value != null && !value.isBlank()) {
                repository.put(key, value);
            }
            return this;
        }

        public Builder addModule(String line) {
            modules.add(line);
            return this;
        }

        public Builder addComponent(String line) {
            components.add(line);
            return this;
        }

        public Builder addRelationship(String line) {
            relationships.add(line);
            return this;
        }

        public Builder addEndpoint(String line) {
            endpoints.add(line);
            return this;
        }

        public Builder addDataModel(String line) {
            dataModels.add(line);
            return this;
        }

        public Builder addConfiguration(String line) {
            configuration.add(line);
            return this;
        }

        public Builder addDocumentation(String line) {
            documentation.add(line);
            return this;
        }

        public Builder addSourceSnippet(String block) {
            sourceSnippets.add(block);
            return this;
        }

        public Builder addAnalysisNote(String line) {
            analysisNotes.add(line);
            return this;
        }

        public Builder include(String item) {
            included.add(item);
            return this;
        }

        public Builder exclude(String item) {
            excluded.add(item);
            return this;
        }

        public ContextDraft build() {
            return new ContextDraft(this);
        }
    }
}
