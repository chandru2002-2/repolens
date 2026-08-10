package io.repolens.parse;

import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.JavaScriptLanguageProfile;
import io.repolens.parse.profile.LanguageProfiles;
import io.repolens.parse.profile.PythonLanguageProfile;
import io.repolens.parse.profile.TypeScriptLanguageProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossLanguageParseTest {

    @TempDir
    Path tempDir;

    @Test
    void javascriptFallbackExtractsImportsRequireAndFunctions() {
        var captures = new JavaScriptLanguageProfile().fallbackExtract("""
                import util from "./util";
                const fs = require("./fs-helper");
                export function run() {}
                export const helper = () => {};
                export class App {}
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("./util")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("./fs-helper")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("run")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("helper")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("App")));
    }

    @Test
    void typescriptFallbackExtractsImportTypeAndTypeSymbols() {
        var captures = new TypeScriptLanguageProfile().fallbackExtract("""
                import type { User } from "./types";
                import { load } from "./loader";
                export interface User {}
                export type Id = string;
                export enum Role { Admin }
                export function loadUser() {}
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("./types")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("./loader")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("interface") && c.text().equals("User")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("type") && c.text().equals("Id")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("enum") && c.text().equals("Role")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("loadUser")));
    }

    @Test
    void pythonFallbackExtractsFromImportAndTopLevelDefs() {
        var captures = new PythonLanguageProfile().fallbackExtract("""
                from .utils import helper
                import package.core
                class Service:
                    def method(self):
                        pass
                def run():
                    pass
                """);
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals(".utils")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("import") && c.text().equals("package.core")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("class") && c.text().equals("Service")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("function") && c.text().equals("run")));
        assertTrue(captures.stream().anyMatch(c -> c.name().equals("method") && c.text().equals("method")));
    }

    @Test
    void buildsJavascriptRepositoryModel() throws Exception {
        write("src/app.js", """
                import util from "./util";
                const helper = require("./helper");
                export function main() {}
                export class App {}
                """);
        write("src/util.js", "export function util() {}");
        write("src/helper.js", "module.exports = {};");

        RepositoryModel model = analyze(List.of("src/app.js", "src/util.js", "src/helper.js"));

        assertEquals(3, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("src/app")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("src/util")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("main") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("App") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("./util")));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("./helper")));
    }

    @Test
    void buildsTypescriptRepositoryModel() throws Exception {
        write("src/index.ts", """
                import type { User } from "./types";
                import { load } from "./loader";
                export function boot() {}
                """);
        write("src/types.ts", """
                export interface User { id: string }
                export type Id = string;
                """);
        write("src/loader.ts", "export function load() {}");

        RepositoryModel model = analyze(List.of("src/index.ts", "src/types.ts", "src/loader.ts"));

        assertEquals(3, model.fileCount());
        // index.ts collapses to parent module identity "src"
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("src")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("User") && s.kind() == SymbolKind.INTERFACE));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Id") && s.kind() == SymbolKind.TYPE));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("boot") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("./types")));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("./loader")));
    }

    @Test
    void buildsPythonRepositoryModel() throws Exception {
        write("app/main.py", """
                from .utils import helper
                import package.core
                class Service:
                    def run(self):
                        pass
                def start():
                    pass
                """);
        write("app/utils.py", "def helper():\n    pass\n");
        write("package/core.py", "VALUE = 1\n");

        RepositoryModel model = analyze(List.of("app/main.py", "app/utils.py", "package/core.py"));

        assertEquals(3, model.fileCount());
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("app/main")));
        assertTrue(model.modules().stream().anyMatch(m -> m.name().equals("app/utils")));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("Service") && s.kind() == SymbolKind.CLASS));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("start") && s.kind() == SymbolKind.FUNCTION));
        assertTrue(model.symbols().stream().anyMatch(s ->
                s.name().equals("run") && s.kind() == SymbolKind.METHOD));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals(".utils")));
        assertTrue(model.imports().stream().anyMatch(i -> i.rawImport().equals("package.core")));
    }

    private void write(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
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
                Repository.local("r1", "cross-lang", tempDir.toString()),
                tempDir,
                new WorkingTreeInventory(files, 100L * files.size(), 0)
        );
    }
}
