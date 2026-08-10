package io.repolens.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Central domain aggregate for RepoLens.
 *
 * <p>Ingestion and parsing populate this model. Analyzers consume it.
 * CLI, Web API, and UI never parse source syntax directly.
 */
public final class RepositoryModel {

    private final Repository repository;
    private final Map<String, SourceFile> filesByPath;
    private final Map<String, Module> modulesById;
    private final Map<String, Symbol> symbolsById;
    private final Map<String, Import> importsById;
    private final Map<String, ExternalDependency> dependenciesById;
    private final Map<String, Relationship> relationshipsById;
    private final List<Metric> metrics;

    private RepositoryModel(Builder builder) {
        this.repository = Objects.requireNonNull(builder.repository, "repository");
        this.filesByPath = Map.copyOf(builder.filesByPath);
        this.modulesById = Map.copyOf(builder.modulesById);
        this.symbolsById = Map.copyOf(builder.symbolsById);
        this.importsById = Map.copyOf(builder.importsById);
        this.dependenciesById = Map.copyOf(builder.dependenciesById);
        this.relationshipsById = Map.copyOf(builder.relationshipsById);
        this.metrics = List.copyOf(builder.metrics);
    }

    public Repository repository() {
        return repository;
    }

    public Collection<SourceFile> files() {
        return filesByPath.values();
    }

    public Optional<SourceFile> findFile(String path) {
        return Optional.ofNullable(filesByPath.get(path));
    }

    public Collection<Module> modules() {
        return modulesById.values();
    }

    public Optional<Module> findModule(String id) {
        return Optional.ofNullable(modulesById.get(id));
    }

    public Collection<Symbol> symbols() {
        return symbolsById.values();
    }

    public Optional<Symbol> findSymbol(String id) {
        return Optional.ofNullable(symbolsById.get(id));
    }

    public Collection<Import> imports() {
        return importsById.values();
    }

    public Collection<ExternalDependency> externalDependencies() {
        return dependenciesById.values();
    }

    public Collection<Relationship> relationships() {
        return relationshipsById.values();
    }

    public List<Metric> metrics() {
        return metrics;
    }

    public int fileCount() {
        return filesByPath.size();
    }

    public int symbolCount() {
        return symbolsById.size();
    }

    public static Builder builder(Repository repository) {
        return new Builder(repository);
    }

    public static final class Builder {
        private final Repository repository;
        private final Map<String, SourceFile> filesByPath = new LinkedHashMap<>();
        private final Map<String, Module> modulesById = new LinkedHashMap<>();
        private final Map<String, Symbol> symbolsById = new LinkedHashMap<>();
        private final Map<String, Import> importsById = new LinkedHashMap<>();
        private final Map<String, ExternalDependency> dependenciesById = new LinkedHashMap<>();
        private final Map<String, Relationship> relationshipsById = new LinkedHashMap<>();
        private final List<Metric> metrics = new ArrayList<>();

        private Builder(Repository repository) {
            this.repository = Objects.requireNonNull(repository, "repository");
        }

        public Builder addFile(SourceFile file) {
            Objects.requireNonNull(file, "file");
            if (filesByPath.containsKey(file.path())) {
                throw new IllegalArgumentException("duplicate file path: " + file.path());
            }
            filesByPath.put(file.path(), file);
            return this;
        }

        public Builder addModule(Module module) {
            Objects.requireNonNull(module, "module");
            if (modulesById.containsKey(module.id())) {
                throw new IllegalArgumentException("duplicate module id: " + module.id());
            }
            modulesById.put(module.id(), module);
            return this;
        }

        public Builder addSymbol(Symbol symbol) {
            Objects.requireNonNull(symbol, "symbol");
            if (symbolsById.containsKey(symbol.id())) {
                throw new IllegalArgumentException("duplicate symbol id: " + symbol.id());
            }
            symbolsById.put(symbol.id(), symbol);
            return this;
        }

        public Builder addImport(Import importDecl) {
            Objects.requireNonNull(importDecl, "importDecl");
            if (importsById.containsKey(importDecl.id())) {
                throw new IllegalArgumentException("duplicate import id: " + importDecl.id());
            }
            importsById.put(importDecl.id(), importDecl);
            return this;
        }

        public Builder addExternalDependency(ExternalDependency dependency) {
            Objects.requireNonNull(dependency, "dependency");
            if (dependenciesById.containsKey(dependency.id())) {
                throw new IllegalArgumentException("duplicate dependency id: " + dependency.id());
            }
            dependenciesById.put(dependency.id(), dependency);
            return this;
        }

        public Builder addRelationship(Relationship relationship) {
            Objects.requireNonNull(relationship, "relationship");
            if (relationshipsById.containsKey(relationship.id())) {
                throw new IllegalArgumentException("duplicate relationship id: " + relationship.id());
            }
            relationshipsById.put(relationship.id(), relationship);
            return this;
        }

        public Builder addMetric(Metric metric) {
            metrics.add(Objects.requireNonNull(metric, "metric"));
            return this;
        }

        public RepositoryModel build() {
            for (Symbol symbol : symbolsById.values()) {
                symbol.moduleId().ifPresent(moduleId -> {
                    if (!modulesById.containsKey(moduleId)) {
                        throw new IllegalStateException(
                                "symbol " + symbol.id() + " references unknown module " + moduleId);
                    }
                });
                symbol.parentSymbolId().ifPresent(parentId -> {
                    if (!symbolsById.containsKey(parentId)) {
                        throw new IllegalStateException(
                                "symbol " + symbol.id() + " references unknown parent " + parentId);
                    }
                });
                if (!filesByPath.containsKey(symbol.location().filePath())) {
                    throw new IllegalStateException(
                            "symbol " + symbol.id() + " references unknown file "
                                    + symbol.location().filePath());
                }
            }
            for (Import importDecl : importsById.values()) {
                if (!filesByPath.containsKey(importDecl.sourceFilePath())) {
                    throw new IllegalStateException(
                            "import " + importDecl.id() + " references unknown file "
                                    + importDecl.sourceFilePath());
                }
            }
            return new RepositoryModel(this);
        }

        /** Snapshot used by tests; returns an unmodifiable view of current files. */
        Map<String, SourceFile> filesView() {
            return Collections.unmodifiableMap(filesByPath);
        }
    }
}
