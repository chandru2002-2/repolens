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

class SpringEndpointExtractionTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsGetMapping() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @GetMapping("/users")
                  public String list() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("UserController.java");
        Endpoint endpoint = singleEndpoint(model);
        assertEquals("GET", endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
        assertEquals(InferenceMethod.ANNOTATION, endpoint.evidence().inferenceMethod());
    }

    @Test
    void extractsPostMapping() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @PostMapping("/users")
                  public String create() { return "ok"; }
                }
                """);
        Endpoint endpoint = singleEndpoint(analyze("UserController.java"));
        assertEquals("POST", endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
    }

    @Test
    void combinesClassAndMethodPath() throws Exception {
        writeController("""
                @RestController
                @RequestMapping("/api")
                public class UserController {
                  @GetMapping("/users")
                  public String list() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("UserController.java");
        Endpoint endpoint = singleEndpoint(model);
        assertEquals("/api/users", endpoint.path());
        assertTrue(model.structuralFacts("usecase").stream()
                .anyMatch(fact -> fact.kind().equals("use_case")
                        && fact.detail().orElse("").equals("route=/api/users")));
    }

    @Test
    void requestMappingWithoutMethodIsUnknown() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @RequestMapping("/users")
                  public String list() { return "ok"; }
                }
                """);
        Endpoint endpoint = singleEndpoint(analyze("UserController.java"));
        assertEquals(Endpoint.UNKNOWN_METHOD, endpoint.httpMethod());
        assertEquals("/users", endpoint.path());
    }

    @Test
    void linksControllerAndHandlerSymbols() throws Exception {
        writeController("""
                @Controller
                @RequestMapping("/owners")
                public class OwnerController {
                  @GetMapping("/{ownerId}")
                  public String showOwner() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("OwnerController.java");
        Endpoint endpoint = singleEndpoint(model);
        assertEquals("/owners/{ownerId}", endpoint.path());
        assertEquals("GET", endpoint.httpMethod());
        String ownerId = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.CLASS && symbol.name().equals("OwnerController"))
                .findFirst()
                .orElseThrow()
                .id();
        String handlerId = model.symbols().stream()
                .filter(symbol -> symbol.kind() == SymbolKind.METHOD && symbol.name().equals("showOwner"))
                .findFirst()
                .orElseThrow()
                .id();
        assertEquals(ownerId, endpoint.ownerTypeId().orElseThrow());
        assertEquals(handlerId, endpoint.handlerMethodId().orElseThrow());
    }

    @Test
    void capturesSourceLocationAndAnnotationEvidence() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @PutMapping("/users")
                  public String replace() { return "ok"; }
                }
                """);
        Endpoint endpoint = singleEndpoint(analyze("UserController.java"));
        assertTrue(endpoint.location().startLine() >= 1);
        assertTrue(endpoint.evidence().location().isPresent());
        assertEquals(endpoint.location().filePath(), endpoint.evidence().location().orElseThrow().filePath());
        assertEquals("@PutMapping", endpoint.evidence().summary().orElseThrow());
        assertEquals("UserController.java", endpoint.location().filePath());
    }

    @Test
    void incompleteMultiPathMappingDoesNotCreateEndpoint() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @GetMapping({"/a", "/b"})
                  public String list() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("UserController.java");
        assertTrue(model.endpoints().isEmpty());
    }

    @Test
    void requestMappingWithMultipleMethodsIsOmitted() throws Exception {
        writeController("""
                @RestController
                public class UserController {
                  @RequestMapping(value = "/users", method = {RequestMethod.GET, RequestMethod.POST})
                  public String list() { return "ok"; }
                }
                """);
        assertTrue(analyze("UserController.java").endpoints().isEmpty());
    }

    private static Endpoint singleEndpoint(RepositoryModel model) {
        assertEquals(1, model.endpoints().size(), () -> "endpoints=" + model.endpoints());
        return model.endpoints().iterator().next();
    }

    private void writeController(String body) throws Exception {
        String fileName = body.contains("class OwnerController") ? "OwnerController.java" : "UserController.java";
        Files.writeString(tempDir.resolve(fileName), """
                package demo;
                import org.springframework.web.bind.annotation.*;
                """ + body);
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
