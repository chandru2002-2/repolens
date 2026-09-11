package io.repolens.parse;

import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
import io.repolens.core.model.SymbolKind;
import io.repolens.core.model.WorkingTreeInventory;
import io.repolens.parse.engine.UnavailableSyntaxEngine;
import io.repolens.parse.profile.JavaLanguageProfile;
import io.repolens.parse.profile.LanguageProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFieldExtractionParseTest {

    @TempDir
    Path tempDir;

    @Test
    void capturesOnlyMemberFieldsNotLocalsParametersOrNull() throws Exception {
        Files.writeString(tempDir.resolve("Owner.java"), """
                package demo;
                public class Owner {
                  private String address;
                  private final java.util.List<Pet> pets = new java.util.ArrayList<>();

                  public Owner(String address) {
                    this.address = address;
                  }

                  public Pet getPet(String compName, boolean ignoreNew) {
                    String compId = null;
                    Pet pet = null;
                    for (Pet candidate : pets) {
                      try {
                      } catch (Exception error) {
                        return null;
                      }
                    }
                    pets.forEach(item -> { String unused = item.toString(); });
                    return pet;
                  }

                  public void setAddress(String address) {
                    this.address = address;
                  }
                }
                """);
        RepositoryModel model = analyze("Owner.java");
        Set<String> fields = model.symbols().stream()
                .filter(s -> s.kind() == SymbolKind.CLASS && s.name().equals("Owner"))
                .flatMap(owner -> model.symbols().stream()
                        .filter(s -> s.kind() == SymbolKind.FIELD)
                        .filter(s -> s.parentSymbolId().orElse("").equals(owner.id()))
                        .map(s -> s.name()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("address", "pets"), fields);
        assertFalse(fields.contains("compId"));
        assertFalse(fields.contains("compName"));
        assertFalse(fields.contains("null"));
        assertFalse(fields.contains("pet"));
        assertFalse(fields.contains("candidate"));
        assertFalse(fields.contains("error"));
        assertFalse(fields.contains("item"));
        assertFalse(fields.contains("unused"));
        assertFalse(fields.contains("ignoreNew"));
    }

    @Test
    void stripLeavesClassLevelFields() {
        String stripped = JavaLanguageProfile.stripMethodAndControlBodies("""
                public class Demo {
                  private int count;
                  public void run() {
                    int local = 1;
                  }
                }
                """);
        assertTrue(stripped.contains("private int count;"));
        assertFalse(stripped.contains("int local"));
    }

    private RepositoryModel analyze(String name) throws Exception {
        WorkingTreeInventory inventory = new WorkingTreeInventory(
                List.of(new WorkingTreeInventory.InventoriedFile(name, 100)),
                400,
                0
        );
        return new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(Repository.local("r1", "demo", tempDir.toString()), tempDir, inventory);
    }
}
