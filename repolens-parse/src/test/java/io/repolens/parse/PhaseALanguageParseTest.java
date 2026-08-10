package io.repolens.parse;

import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.CSharpLanguageProfile;
import io.repolens.parse.profile.GoLanguageProfile;
import io.repolens.parse.profile.KotlinLanguageProfile;
import io.repolens.parse.profile.LanguageProfiles;
import io.repolens.parse.profile.RustLanguageProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A language profiles: Go, Rust, C#, Kotlin.
 */
class PhaseALanguageParseTest {

    @TempDir
    Path tempDir;

    @Test
    void profilesAreRegisteredAndDetectExtensions() {
        assertNotNull(LanguageProfiles.find("demo/util.go"));
        assertEquals("go", LanguageProfiles.find("demo/util.go").id());
        assertEquals("rust", LanguageProfiles.find("src/main.rs").id());
        assertEquals("csharp", LanguageProfiles.find("App.cs").id());
        assertEquals("kotlin", LanguageProfiles.find("Main.kt").id());
        assertEquals("kotlin", LanguageProfiles.find("build.kts").id());
    }

    @Test
    void goFallbackExtractsPackageImportsTypesAndFuncs() {
        var captures = new GoLanguageProfile().fallbackExtract("""
                package app

                import (
                    "demo/util"
                    "fmt"
                )

                type Service struct{}
                type Handler interface {
                    Handle()
                }
                type ID = string

                func New() *Service { return nil }
                func (s *Service) Run() {}
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("module") && c.text().equals("app")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("demo/util")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("fmt")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Service")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("interface") && c.text().equals("Handler")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("type") && c.text().equals("ID")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("New")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("Run")));
    }

    @Test
    void goEmptyAndCommentOnlyDoNotCrash() {
        assertTrue(new GoLanguageProfile().fallbackExtract("").isEmpty()
                || new GoLanguageProfile().fallbackExtract("").stream().noneMatch(c -> c.text().isBlank() && c.name().equals("class")));
        assertTrue(new GoLanguageProfile().fallbackExtract("// just a comment\n").stream()
                .noneMatch(c -> c.name().equals("function")));
    }

    @Test
    void buildsGoRepositoryModel() throws Exception {
        write("demo/util/util.go", """
                package util

                func Helper() string { return "ok" }
                """);
        write("demo/app/app.go", """
                package app

                import "demo/util"

                type Service struct{}

                func Main() {}
                func (s *Service) Start() {}
                """);

        RepositoryModel model = analyze(List.of("demo/util/util.go", "demo/app/app.go"));

        assertEquals(2, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo/app") && m.language().equals("go")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo/util")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Service") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Main") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Start") && s.kind() == SymbolKind.METHOD));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("demo/util")));
    }

    @Test
    void rustFallbackExtractsUseStructsTraitsAndFns() {
        var captures = new RustLanguageProfile().fallbackExtract("""
                use crate::util::helper;
                use super::other::Thing;

                pub struct Service {}
                pub enum Role { Admin }
                pub trait Runner { fn run(&self); }
                pub type Id = u64;

                pub fn start() {}

                impl Service {
                    pub fn boot(&self) {}
                }
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("util")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("other")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Service")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("enum") && c.text().equals("Role")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("interface") && c.text().equals("Runner")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("type") && c.text().equals("Id")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("start")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("boot")));
    }

    @Test
    void buildsRustRepositoryModel() throws Exception {
        write("util.rs", """
                pub fn helper() -> i32 { 1 }
                """);
        write("main.rs", """
                use crate::util::helper;

                pub struct App {}

                pub fn main() {}
                """);

        RepositoryModel model = analyze(List.of("util.rs", "main.rs"));

        assertEquals(2, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("main") && m.language().equals("rust")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("util")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("main") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("util")));
    }

    @Test
    void csharpFallbackExtractsNamespaceUsingAndTypes() {
        var captures = new CSharpLanguageProfile().fallbackExtract("""
                namespace Demo.App;

                using Demo.Core;
                using static System.Math;

                public class Service {
                  public void Run() {}
                }
                public interface IHandler {}
                public enum Role { Admin }
                public struct Point {}
                public record User(string Name);
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("module") && c.text().equals("Demo.App")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("Demo.Core")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("System.Math")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Service")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("interface") && c.text().equals("IHandler")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("enum") && c.text().equals("Role")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("type") && c.text().equals("Point")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("type") && c.text().equals("User")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("Run")));
    }

    @Test
    void buildsCSharpRepositoryModel() throws Exception {
        write("Demo/Core/Core.cs", """
                namespace Demo.Core;
                public class Core {}
                """);
        write("Demo/App/App.cs", """
                namespace Demo.App;
                using Demo.Core;
                public class App {
                  public void Run() {}
                }
                """);

        RepositoryModel model = analyze(List.of("Demo/Core/Core.cs", "Demo/App/App.cs"));

        assertEquals(2, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("Demo.App") && m.language().equals("csharp")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("Demo.Core")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Run") && s.kind() == SymbolKind.METHOD));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("Demo.Core")));
    }

    @Test
    void kotlinFallbackExtractsPackageImportsAndSymbols() {
        var captures = new KotlinLanguageProfile().fallbackExtract("""
                package demo.app

                import demo.core.Core

                class Service {
                    fun run() {}
                }
                interface Handler
                object Hub
                enum class Role { Admin }
                fun start() {}
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("module") && c.text().equals("demo.app")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("demo.core.Core")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Service")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("interface") && c.text().equals("Handler")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Hub")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("enum") && c.text().equals("Role")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("start")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("run")));
    }

    @Test
    void buildsKotlinRepositoryModel() throws Exception {
        write("demo/core/Core.kt", """
                package demo.core
                class Core
                """);
        write("demo/app/App.kt", """
                package demo.app
                import demo.core.Core
                class App {
                    fun run() {}
                }
                fun boot() {}
                """);

        RepositoryModel model = analyze(List.of("demo/core/Core.kt", "demo/app/App.kt"));

        assertEquals(2, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo.app") && m.language().equals("kotlin")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo.core")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("boot") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("demo.core.Core")));
    }

    @Test
    void mixedJavaAndGoRepositoryBuildsCombinedModel() throws Exception {
        write("src/demo/core/Core.java", """
                package demo.core;
                public class Core {}
                """);
        write("demo/util/util.go", """
                package util
                func Helper() {}
                """);

        RepositoryModel model = analyze(List.of("src/demo/core/Core.java", "demo/util/util.go"));

        assertEquals(2, model.fileCount());
        assertTrue(model.files().stream().anyMatch(f -> f.language().equals("java")));
        assertTrue(model.files().stream().anyMatch(f -> f.language().equals("go")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo.core") && m.language().equals("java")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("demo/util") && m.language().equals("go")));
        assertTrue(model.symbols().stream().anyMatch(s -> s.name().equals("Core") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Helper") && s.kind() == SymbolKind.FUNCTION));
    }

    @Test
    void malformedSimpleFilesDoNotCrash() throws Exception {
        write("broken.go", "package \n func (");
        write("broken.rs", "use crate::\nstruct");
        write("broken.cs", "namespace {\nclass");
        write("broken.kt", "package\nfun (");

        RepositoryModel model = analyze(List.of("broken.go", "broken.rs", "broken.cs", "broken.kt"));
        assertEquals(4, model.fileCount());
    }

    private void write(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }

    private RepositoryModel analyze(List<String> paths) {
        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();
        for (String path : paths) {
            files.add(new WorkingTreeInventory.InventoriedFile(path, 100));
        }
        ProfiledSourceAnalyzer analyzer = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        );
        return analyzer.analyze(
                Repository.local("r1", "phase-a", tempDir.toString()),
                tempDir,
                new WorkingTreeInventory(files, 100L * files.size(), 0)
        );
    }
}
