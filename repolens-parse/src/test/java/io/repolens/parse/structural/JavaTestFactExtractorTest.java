package io.repolens.parse.structural;

import io.repolens.core.model.InferenceMethod;
import io.repolens.core.model.Repository;
import io.repolens.core.model.RepositoryModel;
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

class JavaTestFactExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversJunit5TestMethod() throws Exception {
        write("UserServiceTest.java", """
                package demo;
                import org.junit.jupiter.api.Test;
                class UserServiceTest {
                  @Test
                  public void loads() { }
                }
                """);
        RepositoryModel model = analyze("UserServiceTest.java");
        io.repolens.core.model.Test method = testNamed(model, "loads");
        assertEquals("junit5", method.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.ANNOTATION, method.evidence().inferenceMethod());
        assertEquals("@Test", method.evidence().summary().orElseThrow());
        assertTrue(model.tests().stream().anyMatch(test ->
                symbolName(model, test.symbolId()).equals("UserServiceTest")));
    }

    @Test
    void discoversParameterizedTest() throws Exception {
        write("UserServiceTest.java", """
                package demo;
                import org.junit.jupiter.params.ParameterizedTest;
                class UserServiceTest {
                  @ParameterizedTest
                  public void loads(String input) { }
                }
                """);
        io.repolens.core.model.Test method = testNamed(analyze("UserServiceTest.java"), "loads");
        assertEquals("junit5", method.frameworkHint().orElseThrow());
        assertEquals("@ParameterizedTest", method.evidence().summary().orElseThrow());
    }

    @Test
    void discoversNestedType() throws Exception {
        write("OuterTest.java", """
                package demo;
                import org.junit.jupiter.api.Nested;
                class OuterTest {
                  @Nested
                  class Inner {
                  }
                }
                """);
        RepositoryModel model = analyze("OuterTest.java");
        io.repolens.core.model.Test nested = testNamed(model, "Inner");
        assertEquals("junit5", nested.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.ANNOTATION, nested.evidence().inferenceMethod());
        assertEquals("@Nested", nested.evidence().summary().orElseThrow());
    }

    @Test
    void discoversJunit4TestMethod() throws Exception {
        write("LegacyTest.java", """
                package demo;
                import org.junit.Test;
                public class LegacyTest {
                  @Test
                  public void loads() { }
                }
                """);
        io.repolens.core.model.Test method = testNamed(analyze("LegacyTest.java"), "loads");
        assertEquals("junit4", method.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.ANNOTATION, method.evidence().inferenceMethod());
    }

    @Test
    void discoversClassFromFileAndTypeNameWithoutAnnotations() throws Exception {
        write("WidgetIT.java", """
                package demo;
                public class WidgetIT {
                  public void run() { }
                }
                """);
        RepositoryModel model = analyze("WidgetIT.java");
        io.repolens.core.model.Test type = testNamed(model, "WidgetIT");
        assertEquals(InferenceMethod.NAME_HEURISTIC, type.evidence().inferenceMethod());
        assertTrue(type.evidence().summary().orElse("").contains("WidgetIT"));
        assertTrue(model.tests().stream().noneMatch(test ->
                symbolName(model, test.symbolId()).equals("run")));
    }

    @Test
    void discoversPrimaryTypeFromFileNameWhenClassNameDoesNotMatch() throws Exception {
        write("SampleTest.java", """
                package demo;
                public class Sample {
                  public void run() { }
                }
                """);
        RepositoryModel model = analyze("SampleTest.java");
        io.repolens.core.model.Test type = testNamed(model, "Sample");
        assertEquals(InferenceMethod.NAME_HEURISTIC, type.evidence().inferenceMethod());
        assertTrue(type.evidence().summary().orElse("").contains("SampleTest.java"));
    }

    @Test
    void capturesEvidenceLocation() throws Exception {
        write("UserServiceTest.java", """
                package demo;
                import org.junit.jupiter.api.Test;
                class UserServiceTest {
                  @Test
                  public void loads() { }
                }
                """);
        io.repolens.core.model.Test method = testNamed(analyze("UserServiceTest.java"), "loads");
        assertTrue(method.location().startLine() >= 1);
        assertEquals("UserServiceTest.java", method.location().filePath());
        assertTrue(method.evidence().location().isPresent());
        assertEquals("UserServiceTest.java", method.evidence().location().orElseThrow().filePath());
    }

    @Test
    void doesNotDuplicateTheSameSymbol() throws Exception {
        write("UserServiceTest.java", """
                package demo;
                import org.junit.jupiter.api.Test;
                class UserServiceTest {
                  @Test
                  @Test
                  public void loads() { }
                }
                """);
        RepositoryModel model = analyze("UserServiceTest.java");
        long loads = model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId()).equals("loads"))
                .count();
        assertEquals(1, loads);
        long types = model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId()).equals("UserServiceTest"))
                .count();
        assertEquals(1, types);
        io.repolens.core.model.Test method = testNamed(model, "loads");
        assertEquals(InferenceMethod.ANNOTATION, method.evidence().inferenceMethod());
    }

    @Test
    void doesNotTreatMethodNamedTestAsATest() throws Exception {
        write("Probe.java", """
                package demo;
                public class Probe {
                  public int test() { return 1; }
                  public void testConnection() { }
                }
                """);
        RepositoryModel model = analyze("Probe.java");
        assertTrue(model.tests().isEmpty());
    }

    private io.repolens.core.model.Test testNamed(RepositoryModel model, String symbolName) {
        return model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId()).equals(symbolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tests=" + model.tests()
                        + " symbols=" + model.symbols()));
    }

    private static String symbolName(RepositoryModel model, String symbolId) {
        return model.findSymbol(symbolId).orElseThrow().name();
    }

    private void write(String name, String source) throws Exception {
        Files.writeString(tempDir.resolve(name), source);
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
