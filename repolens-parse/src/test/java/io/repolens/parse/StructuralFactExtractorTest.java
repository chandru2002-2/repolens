package io.repolens.parse;

import io.repolens.core.model.RelationshipType;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.LanguageProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                  void next(Status s) {
                    switch (s) {
                      case PENDING: break;
                      case PROCESSING: break;
                      case COMPLETED: break;
                    }
                  }
                }
                """);
        Files.writeString(tempDir.resolve("docker-compose.yml"), """
                services:
                  api:
                    image: demo/api:1
                    ports:
                      - "8080:8080"
                  mysql:
                    image: mysql:8
                """);
        Files.writeString(tempDir.resolve("application.properties"),
                "spring.datasource.url=jdbc:mysql://localhost:3306/demo\n");

        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();
        for (String name : List.of(
                "UserController.java", "UserService.java", "UserRepository.java",
                "User.java", "Order.java", "Status.java",
                "docker-compose.yml", "application.properties"
        )) {
            files.add(new WorkingTreeInventory.InventoriedFile(name, 100));
        }
        WorkingTreeInventory inventory = new WorkingTreeInventory(files, 800, 0);

        RepositoryModel model = new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);

        assertTrue(model.structuralFacts("sequence").stream().anyMatch(f -> f.kind().equals("controller")));
        assertTrue(model.structuralFacts("sequence").stream().anyMatch(f -> f.kind().equals("service")));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().equals("entity")));
        assertTrue(model.structuralFacts("er").stream().anyMatch(f -> f.kind().contains("one") || f.kind().contains("many")));
        assertTrue(model.structuralFacts("deployment").stream().anyMatch(f -> f.label().equals("api") || f.label().equals("mysql")));
        assertTrue(model.structuralFacts("usecase").stream().anyMatch(f -> f.kind().equals("use_case")));
        assertTrue(model.structuralFacts("state").stream().anyMatch(f -> f.kind().equals("state")));
        assertTrue(model.structuralFacts("activity").stream().anyMatch(f -> f.kind().equals("start")));
        assertTrue(model.structuralFacts("dfd").stream().anyMatch(f -> f.kind().equals("process") || f.kind().equals("data_store")));
        assertTrue(model.relationships().stream().anyMatch(r -> r.type() == RelationshipType.CALLS)
                || model.structuralFacts("sequence").stream().anyMatch(f -> f.kind().equals("call")));
        assertFalse(model.symbols().stream().filter(s -> s.kind() == SymbolKind.CLASS).toList().isEmpty());
    }
}
