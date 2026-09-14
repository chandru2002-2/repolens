package io.repolens.parse.structural;

import io.repolens.core.model.Endpoint;
import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.ProfiledSourceAnalyzer;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.LanguageProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEndpointExtractionTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsFlaskGetRoute() throws Exception {
        write("app.py", """
                from flask import Flask
                app = Flask(__name__)

                @app.route("/users")
                def list_users():
                    return "ok"
                """);
        Endpoint endpoint = singleEndpoint(analyze("app.py"));
        assertEquals("GET", endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
        assertEquals(InferenceMethod.ANNOTATION, endpoint.evidence().inferenceMethod());
        assertEquals("@app.route", endpoint.evidence().summary().orElseThrow());
    }

    @Test
    void extractsFlaskExplicitPostRoute() throws Exception {
        write("app.py", """
                from flask import Flask
                app = Flask(__name__)

                @app.route("/users", methods=["POST"])
                def create_user():
                    return "ok"
                """);
        Endpoint endpoint = singleEndpoint(analyze("app.py"));
        assertEquals("POST", endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
    }

    @Test
    void extractsFastApiGetRoute() throws Exception {
        write("main.py", """
                from fastapi import FastAPI
                app = FastAPI()

                @app.get("/items")
                def list_items():
                    return []
                """);
        Endpoint endpoint = singleEndpoint(analyze("main.py"));
        assertEquals("GET", endpoint.httpMethod());
        assertEquals("/items", endpoint.path());
        assertEquals("@app.get", endpoint.evidence().summary().orElseThrow());
        assertEquals(InferenceMethod.ANNOTATION, endpoint.evidence().inferenceMethod());
    }

    @Test
    void extractsFastApiPostRoute() throws Exception {
        write("main.py", """
                from fastapi import FastAPI
                app = FastAPI()

                @app.post("/items")
                def create_item():
                    return {}
                """);
        Endpoint endpoint = singleEndpoint(analyze("main.py"));
        assertEquals("POST", endpoint.httpMethod());
        assertEquals("/items", endpoint.path());
    }

    @Test
    void resolvesModuleLevelHandler() throws Exception {
        write("app.py", """
                from flask import Flask
                app = Flask(__name__)

                @app.route("/users")
                def list_users():
                    return "ok"
                """);
        RepositoryModel model = analyze("app.py");
        Endpoint endpoint = singleEndpoint(model);
        String handlerId = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.FUNCTION && symbol.name().equals("list_users"))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(handlerId, endpoint.handlerMethodId().orElseThrow());
        assertTrue(endpoint.ownerTypeId().isEmpty());
    }

    @Test
    void resolvesClassMethodHandlerWhenPresent() throws Exception {
        write("views.py", """
                from flask import Flask
                app = Flask(__name__)

                class UserViews:
                    @app.route("/users")
                    def list_users(self):
                        return "ok"
                """);
        RepositoryModel model = analyze("views.py");
        Endpoint endpoint = singleEndpoint(model);
        String ownerId = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.CLASS && symbol.name().equals("UserViews"))
                .findFirst()
                .orElseThrow()
                .id();
        String handlerId = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.METHOD && symbol.name().equals("list_users"))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(handlerId, endpoint.handlerMethodId().orElseThrow());
        assertEquals(ownerId, endpoint.ownerTypeId().orElseThrow());
    }

    @Test
    void skipsAmbiguousFlaskMethods() throws Exception {
        write("app.py", """
                from flask import Flask
                app = Flask(__name__)

                @app.route("/users", methods=["GET", "POST"])
                def users():
                    return "ok"
                """);
        assertTrue(analyze("app.py").endpoints().isEmpty());
    }

    @Test
    void skipsAmbiguousMultiPathFlaskRoute() throws Exception {
        write("app.py", """
                from flask import Flask
                app = Flask(__name__)

                @app.route("/a", "/b")
                def users():
                    return "ok"
                """);
        assertTrue(analyze("app.py").endpoints().isEmpty());
    }

    @Test
    void unrelatedDecoratorsAndFunctionsDoNotCreateEndpoints() throws Exception {
        write("util.py", """
                from dataclasses import dataclass

                @dataclass
                class User:
                    name: str

                @property
                def label():
                    return "x"

                def helper():
                    return 1
                """);
        assertTrue(analyze("util.py").endpoints().isEmpty());
    }

    @Test
    void endpointOrderFollowsSourceOrder() throws Exception {
        write("main.py", """
                from fastapi import FastAPI
                app = FastAPI()

                @app.get("/items")
                def list_items():
                    return []

                @app.post("/items")
                def create_item():
                    return {}
                """);
        List<Endpoint> endpoints = List.copyOf(analyze("main.py").endpoints());
        assertEquals(2, endpoints.size());
        assertEquals("GET", endpoints.get(0).httpMethod());
        assertEquals("/items", endpoints.get(0).path());
        assertEquals("POST", endpoints.get(1).httpMethod());
        assertEquals("/items", endpoints.get(1).path());
        assertEquals("endpoint:1", endpoints.get(0).id());
        assertEquals("endpoint:2", endpoints.get(1).id());
    }

    private static Endpoint singleEndpoint(RepositoryModel model) {
        assertEquals(1, model.endpoints().size(), () -> "endpoints=" + model.endpoints());
        return model.endpoints().iterator().next();
    }

    private void write(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }

    private RepositoryModel analyze(String... names) throws Exception {
        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();
        for (String name : names) {
            files.add(new WorkingTreeInventory.InventoriedFile(name, 200));
        }
        WorkingTreeInventory inventory = new WorkingTreeInventory(files, 800, 0);
        return new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);
    }
}
