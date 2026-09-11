package io.repolens.parse;

import io.repolens.core.model.Relationship;
import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.LanguageProfiles;
import io.repolens.parse.structural.CallConfidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuralFactExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsSequenceErActivityDeploymentUseCaseAndStateFacts() throws Exception {
        Files.writeString(tempDir.resolve("UserController.java"), """
                package demo;
                import org.springframework.web.bind.annotation.*;
                @RestController
                public class UserController {
                  private UserService userService;
                  @GetMapping("/users")
                  public User getUser() { return userService.findUser(); }
                }
                """);
        Files.writeString(tempDir.resolve("UserService.java"), """
                package demo;
                import org.springframework.stereotype.Service;
                @Service
                public class UserService {
                  private UserRepository userRepository;
                  public User findUser() {
                    if (true) { return userRepository.findById(); }
                    return null;
                  }
                }
                """);
        Files.writeString(tempDir.resolve("UserRepository.java"), """
                package demo;
                import org.springframework.stereotype.Repository;
                @Repository
                public class UserRepository {
                  public User findById() { return null; }
                }
                """);
        Files.writeString(tempDir.resolve("User.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                @Table(name = "users")
                public class User {
                  @Id private Long id;
                  @OneToMany private java.util.List<Order> orders;
                }
                """);
        Files.writeString(tempDir.resolve("Order.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Order {
                  @Id private Long id;
                  @ManyToOne private User user;
                }
                """);
        Files.writeString(tempDir.resolve("Status.java"), """
                package demo;
                public enum Status { PENDING, PROCESSING, COMPLETED }
                public class Workflow {
                  Status next(Status s) {
                    switch (s) {
                      case PENDING: return Status.PROCESSING;
                      case PROCESSING: return Status.COMPLETED;
                      case COMPLETED: return Status.COMPLETED;
                    }
                    return s;
                  }
                }
                """);
        Files.writeString(tempDir.resolve("docker-compose.yml"), """
                services:
                  api:
                    image: demo/api:1
                    ports:
                      - "8080:8080"
                    depends_on:
                      - mysql
                  mysql:
                    image: mysql:8
                """);
        Files.writeString(tempDir.resolve("application.properties"),
                "spring.datasource.url=jdbc:mysql://localhost:3306/demo\n");

        RepositoryModel model = analyze(
                "UserController.java", "UserService.java", "UserRepository.java",
                "User.java", "Order.java", "Status.java",
                "docker-compose.yml", "application.properties"
        );

        assertTrue(model.structuralFacts("sequence").stream().anyMatch(f -> f.kind().equals("controller")));
        assertTrue(model.structuralFacts("sequence").stream().anyMatch(f -> f.kind().equals("service")));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().equals("entity")));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().equals("one_to_many")));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().equals("many_to_one")));
        assertTrue(model.structuralFacts("deployment").stream().anyMatch(f -> f.label().equals("api")));
        assertTrue(model.structuralFacts("deployment").stream()
                .anyMatch(f -> f.kind().equals("links") && f.label().equals("api->mysql")));
        assertTrue(model.structuralFacts("usecase").stream().anyMatch(f -> f.kind().equals("use_case")));
        assertTrue(model.structuralFacts("state").stream().anyMatch(f -> f.kind().equals("state")));
        assertTrue(model.structuralFacts("state").stream()
                .anyMatch(f -> f.kind().equals("transition") && f.label().equals("PENDING->PROCESSING")));
        assertTrue(model.structuralFacts("activity").stream().anyMatch(f -> f.kind().equals("start")));
        assertTrue(model.structuralFacts("dfd").stream().anyMatch(f -> f.kind().equals("process")));
        assertTrue(model.relationships().stream().anyMatch(r -> r.type() == RelationshipType.CALLS));
        assertFalse(model.symbols().stream().filter(s -> s.kind() == SymbolKind.CLASS).toList().isEmpty());
    }

    @Test
    void enumDeclarationAloneDoesNotCreateTransitions() throws Exception {
        Files.writeString(tempDir.resolve("Status.java"), """
                package demo;
                public enum Status { PENDING, PROCESSING, COMPLETED }
                """);
        RepositoryModel model = analyze("Status.java");
        assertEquals(3, model.structuralFacts("state").stream().filter(f -> f.kind().equals("state")).count());
        assertEquals(0, model.structuralFacts("state").stream().filter(f -> f.kind().equals("transition")).count());
    }

    @Test
    void switchCasesWithoutAssignmentsDoNotCreateTransitions() throws Exception {
        Files.writeString(tempDir.resolve("Status.java"), """
                package demo;
                public enum Status { PENDING, PROCESSING, COMPLETED }
                public class Workflow {
                  void next(Status s) {
                    switch (s) {
                      case PENDING: break;
                      case PROCESSING: break;
                      case COMPLETED: break;
                    }
                  }
                }
                """);
        RepositoryModel model = analyze("Status.java");
        assertTrue(model.structuralFacts("state").stream().anyMatch(f -> f.label().equals("PENDING")));
        assertEquals(0, model.structuralFacts("state").stream().filter(f -> f.kind().equals("transition")).count());
    }

    @Test
    void explicitSwitchReturnCreatesTransitions() throws Exception {
        Files.writeString(tempDir.resolve("Status.java"), """
                package demo;
                public enum Status { PENDING, DONE }
                public class Workflow {
                  Status advance(Status s) {
                    switch (s) {
                      case PENDING: return Status.DONE;
                      case DONE: return Status.DONE;
                    }
                    return s;
                  }
                }
                """);
        RepositoryModel model = analyze("Status.java");
        assertTrue(model.structuralFacts("state").stream()
                .anyMatch(f -> f.kind().equals("transition") && f.label().equals("PENDING->DONE")));
    }

    @Test
    void jpaRelationshipsCaptureCardinalityAndMappedBy() throws Exception {
        Files.writeString(tempDir.resolve("User.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class User {
                  @Id private Long id;
                  @OneToMany(mappedBy = "user")
                  private java.util.List<Order> orders;
                  @ManyToMany
                  private java.util.Set<Tag> tags;
                }
                """);
        Files.writeString(tempDir.resolve("Order.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Order {
                  @Id private Long id;
                  @ManyToOne
                  @JoinColumn(name = "user_id")
                  private User user;
                }
                """);
        Files.writeString(tempDir.resolve("Tag.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Tag {
                  @Id private Long id;
                }
                """);
        RepositoryModel model = analyze("User.java", "Order.java", "Tag.java");
        assertTrue(model.structuralFacts("er").stream()
                .anyMatch(f -> f.kind().equals("one_to_many")
                        && f.detail().orElse("").contains("mappedBy=user")),
                () -> "er facts=" + model.structuralFacts("er"));
        assertTrue(model.structuralFacts("er").stream()
                .anyMatch(f -> f.kind().equals("many_to_one")
                        && f.detail().orElse("").contains("joinColumn=user_id")),
                () -> "er facts=" + model.structuralFacts("er"));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().equals("many_to_many")));
        assertTrue(model.structuralFacts("er").stream()
                .filter(f -> f.kind().equals("entity"))
                .anyMatch(f -> f.label().equals("Tag")));
    }

    @Test
    void jpaOneToManyJoinColumnAndRawListResolveWhenEntityExists() throws Exception {
        Files.writeString(tempDir.resolve("Owner.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Owner {
                  @Id private Long id;
                  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
                  @JoinColumn(name = "owner_id")
                  @OrderBy("name")
                  private final java.util.List<Pet> pets = new java.util.ArrayList<>();
                }
                """);
        Files.writeString(tempDir.resolve("Pet.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Pet {
                  @Id private Long id;
                  @ManyToOne
                  @JoinColumn(name = "type_id")
                  private PetType type;
                }
                """);
        Files.writeString(tempDir.resolve("PetType.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class PetType {
                  @Id private Long id;
                }
                """);
        Files.writeString(tempDir.resolve("Visit.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Visit {
                  @Id private Long id;
                  @OneToMany
                  @JoinColumn(name = "pet_id")
                  private java.util.List visits;
                }
                """);
        RepositoryModel typed = analyze("Owner.java", "Pet.java", "PetType.java");
        assertTrue(typed.structuralFacts("er").stream()
                .anyMatch(f -> f.kind().equals("one_to_many")
                        && f.label().equals("Owner->Pet")
                        && f.detail().orElse("").contains("joinColumn=owner_id")),
                () -> "er facts=" + typed.structuralFacts("er"));
        assertTrue(typed.structuralFacts("er").stream()
                .anyMatch(f -> f.kind().equals("many_to_one") && f.label().equals("Pet->PetType")));

        Files.writeString(tempDir.resolve("Herd.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Herd {
                  @Id private Long id;
                  @OneToMany
                  @JoinColumn(name = "herd_id")
                  private java.util.List pets;
                }
                """);
        RepositoryModel raw = analyze("Herd.java", "Pet.java");
        assertTrue(raw.structuralFacts("er").stream()
                .anyMatch(f -> f.kind().equals("one_to_many") && f.label().equals("Herd->Pet")),
                () -> "er facts=" + raw.structuralFacts("er"));
    }

    @Test
    void useCaseLabelPreservesMappingPath() throws Exception {
        Files.writeString(tempDir.resolve("OwnerController.java"), """
                package demo;
                import org.springframework.web.bind.annotation.*;
                @Controller
                @RequestMapping("/owners")
                public class OwnerController {
                  @GetMapping("/new")
                  public String initCreationForm() { return "ok"; }
                  @GetMapping("/{ownerId}")
                  public String showOwner() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("OwnerController.java");
        assertTrue(model.structuralFacts("usecase").stream()
                .anyMatch(f -> f.kind().equals("use_case") && f.label().equals("/owners/new")));
        assertTrue(model.structuralFacts("usecase").stream()
                .anyMatch(f -> f.kind().equals("use_case") && f.label().equals("/owners/{ownerId}")));
        assertTrue(model.structuralFacts("usecase").stream()
                .noneMatch(f -> f.kind().equals("use_case") && f.label().equals("New")));
    }

    @Test
    void springCombinesClassAndMethodMappings() throws Exception {
        Files.writeString(tempDir.resolve("UserController.java"), """
                package demo;
                import org.springframework.web.bind.annotation.*;
                @RestController
                @RequestMapping("/api")
                public class UserController {
                  @GetMapping("/users")
                  public String list() { return "ok"; }
                }
                """);
        RepositoryModel model = analyze("UserController.java");
        assertTrue(model.structuralFacts("usecase").stream()
                .anyMatch(f -> f.kind().equals("use_case")
                        && f.detail().orElse("").equals("route=/api/users")));
    }

    @Test
    void constructorInjectionYieldsHighConfidenceCalls() throws Exception {
        Files.writeString(tempDir.resolve("UserService.java"), """
                package demo;
                public class UserService {
                  public void find() {}
                }
                """);
        Files.writeString(tempDir.resolve("UserController.java"), """
                package demo;
                public class UserController {
                  private final UserService userService;
                  public UserController(UserService userService) {
                    this.userService = userService;
                  }
                  public void run() { userService.find(); }
                }
                """);
        RepositoryModel model = analyze("UserController.java", "UserService.java");
        List<Relationship> calls = model.relationships().stream()
                .filter(r -> r.type() == RelationshipType.CALLS)
                .toList();
        assertEquals(1, calls.size());
        assertEquals(CallConfidence.HIGH, calls.getFirst().confidence(), 0.001);
        assertTrue(calls.getFirst().provenance().orElse("").contains("confidence=high"));
    }

    @Test
    void fieldInjectionAndStaticCallsResolve() throws Exception {
        Files.writeString(tempDir.resolve("Utils.java"), """
                package demo;
                public class Utils {
                  public static String id() { return "1"; }
                }
                """);
        Files.writeString(tempDir.resolve("UserService.java"), """
                package demo;
                public class UserService {
                  public void find() {}
                }
                """);
        Files.writeString(tempDir.resolve("UserController.java"), """
                package demo;
                public class UserController {
                  private UserService userService;
                  public void run() {
                    userService.find();
                    Utils.id();
                  }
                }
                """);
        RepositoryModel model = analyze("UserController.java", "UserService.java", "Utils.java");
        assertTrue(model.relationships().stream()
                .filter(r -> r.type() == RelationshipType.CALLS)
                .anyMatch(r -> r.confidence() >= CallConfidence.HIGH - 0.01));
        assertTrue(model.structuralFacts("sequence").stream()
                .anyMatch(f -> f.kind().equals("call") && f.label().contains("Utils")));
    }

    @Test
    void setterInjectionResolvesReceiver() throws Exception {
        Files.writeString(tempDir.resolve("UserService.java"), """
                package demo;
                public class UserService {
                  public void find() {}
                }
                """);
        Files.writeString(tempDir.resolve("UserController.java"), """
                package demo;
                public class UserController {
                  private UserService userService;
                  public void setUserService(UserService userService) {
                    this.userService = userService;
                  }
                  public void run() { userService.find(); }
                }
                """);
        RepositoryModel model = analyze("UserController.java", "UserService.java");
        assertTrue(model.relationships().stream().anyMatch(r -> r.type() == RelationshipType.CALLS));
    }

    @Test
    void thisAndSuperCallsDoNotCreateInterTypeRelationships() throws Exception {
        Files.writeString(tempDir.resolve("Base.java"), """
                package demo;
                public class Base {
                  protected void helper() {}
                }
                """);
        Files.writeString(tempDir.resolve("Child.java"), """
                package demo;
                public class Child extends Base {
                  public void run() {
                    this.helper();
                    super.helper();
                  }
                  private void helper() {}
                }
                """);
        RepositoryModel model = analyze("Base.java", "Child.java");
        assertEquals(0, model.relationships().stream().filter(r -> r.type() == RelationshipType.CALLS).count());
    }

    @Test
    void controlKeywordsAreNotTreatedAsCalls() throws Exception {
        Files.writeString(tempDir.resolve("Demo.java"), """
                package demo;
                public class Demo {
                  public void run() {
                    if (true) { return; }
                    for (int i = 0; i < 1; i++) {}
                    while (false) {}
                    switch (1) { default: break; }
                  }
                }
                """);
        RepositoryModel model = analyze("Demo.java");
        assertEquals(0, model.relationships().stream().filter(r -> r.type() == RelationshipType.CALLS).count());
        assertEquals(0, model.structuralFacts("sequence").stream().filter(f -> f.kind().equals("call")).count());
    }

    @Test
    void composeOrderAloneDoesNotCreateLinks() throws Exception {
        Files.writeString(tempDir.resolve("docker-compose.yml"), """
                services:
                  api:
                    image: demo/api:1
                  mysql:
                    image: mysql:8
                """);
        RepositoryModel model = analyze("docker-compose.yml");
        assertTrue(model.structuralFacts("deployment").stream().anyMatch(f -> f.label().equals("api")));
        assertTrue(model.structuralFacts("deployment").stream().anyMatch(f -> f.label().equals("mysql")));
        assertEquals(0, model.structuralFacts("deployment").stream().filter(f -> f.kind().equals("links")).count());
    }

    @Test
    void entityWithoutRelationshipsStillEmitsEntityFact() throws Exception {
        Files.writeString(tempDir.resolve("Solo.java"), """
                package demo;
                import jakarta.persistence.*;
                @Entity
                public class Solo {
                  @Id private Long id;
                }
                """);
        RepositoryModel model = analyze("Solo.java");
        assertEquals(1, model.structuralFacts("er").stream().filter(f -> f.kind().equals("entity")).count());
        assertEquals(0, model.structuralFacts("er").stream()
                .filter(f -> f.kind().contains("to_") || f.kind().equals("association")).count());
    }

    @Test
    void overloadedMethodsAndGenericsStillResolveFieldCalls() throws Exception {
        Files.writeString(tempDir.resolve("Repo.java"), """
                package demo;
                public class Repo {
                  public String get(String id) { return id; }
                  public String get(int id) { return String.valueOf(id); }
                }
                """);
        Files.writeString(tempDir.resolve("Service.java"), """
                package demo;
                public class Service {
                  private java.util.Optional<Repo> repoHolder;
                  private Repo repo;
                  public String run() { return repo.get("1"); }
                }
                """);
        RepositoryModel model = analyze("Repo.java", "Service.java");
        assertTrue(model.relationships().stream().anyMatch(r -> r.type() == RelationshipType.CALLS));
    }

    @Test
    void nestedClassOwnerStillExtractsActivity() throws Exception {
        Files.writeString(tempDir.resolve("Outer.java"), """
                package demo;
                public class Outer {
                  public void run() {
                    if (true) { helper(); }
                  }
                  private void helper() {}
                  public static class Nested {
                    public void tick() { return; }
                  }
                }
                """);
        RepositoryModel model = analyze("Outer.java");
        assertTrue(model.structuralFacts("activity").stream().anyMatch(f -> f.kind().equals("start")));
        assertTrue(model.structuralFacts("activity").stream().anyMatch(f -> f.kind().equals("decision")));
    }

    private RepositoryModel analyze(String... names) throws Exception {
        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();
        for (String name : names) {
            files.add(new WorkingTreeInventory.InventoriedFile(name, 100));
        }
        WorkingTreeInventory inventory = new WorkingTreeInventory(files, 800, 0);
        return new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);
    }
}
