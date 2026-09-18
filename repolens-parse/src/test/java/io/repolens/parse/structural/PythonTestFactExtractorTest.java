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

class PythonTestFactExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversPytestFunctionInTestsDirectory() throws Exception {
        write("tests/test_users.py", """
                def test_loads():
                    pass
                """);

        RepositoryModel model = analyze("tests/test_users.py");

        io.repolens.core.model.Test test = testNamed(model, "test_loads");

        assertEquals("pytest", test.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.NAME_HEURISTIC,
                test.evidence().inferenceMethod());
    }

    @Test
    void discoversPytestFunctionFromTestFileName() throws Exception {
        write("user_test.py", """
                def test_loads():
                    pass
                """);

        RepositoryModel model = analyze("user_test.py");

        io.repolens.core.model.Test test = testNamed(model, "test_loads");

        assertEquals("pytest", test.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.NAME_HEURISTIC,
                test.evidence().inferenceMethod());
    }

    @Test
    void discoversParametrizedTest() throws Exception {
        write("tests/test_users.py", """
                import pytest

                @pytest.mark.parametrize("value", [1, 2])
                def test_loads(value):
                    pass
                """);

        RepositoryModel model = analyze("tests/test_users.py");

        io.repolens.core.model.Test test = testNamed(model, "test_loads");

        assertEquals("pytest", test.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.ANNOTATION,
                test.evidence().inferenceMethod());
        assertEquals("@pytest.mark.parametrize",
                test.evidence().summary().orElseThrow());
    }

    @Test
    void parametrizedTestDoesNotCreateMultipleTests() throws Exception {
        write("tests/test_users.py", """
                import pytest

                @pytest.mark.parametrize("value", [1, 2, 3])
                def test_loads(value):
                    pass
                """);

        RepositoryModel model = analyze("tests/test_users.py");

        long count = model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId())
                        .equals("test_loads"))
                .count();

        assertEquals(1, count);
    }

    @Test
    void discoversUnittestTestCaseMethod() throws Exception {
        write("tests/test_users.py", """
                import unittest

                class UserTest(unittest.TestCase):
                    def test_loads(self):
                        pass
                """);

        RepositoryModel model = analyze("tests/test_users.py");

        io.repolens.core.model.Test test = testNamed(model, "test_loads");

        assertEquals("unittest", test.frameworkHint().orElseThrow());
        assertEquals(InferenceMethod.FRAMEWORK_CONVENTION,
                test.evidence().inferenceMethod());
    }

    @Test
    void doesNotTreatConftestAsTestFile() throws Exception {
        write("tests/conftest.py", """
                def test_fixture_helper():
                    pass
                """);

        RepositoryModel model = analyze("tests/conftest.py");

        assertTrue(model.tests().isEmpty());
    }

    @Test
    void doesNotTreatHelperOutsideTestPathAsTest() throws Exception {
        write("helpers.py", """
                def test_helper():
                    pass
                """);

        RepositoryModel model = analyze("helpers.py");

        assertTrue(model.tests().isEmpty());
    }

    @Test
    void doesNotTreatProductionFunctionAsTest() throws Exception {
        write("app/service.py", """
                def test_connection():
                    pass
                """);

        RepositoryModel model = analyze("app/service.py");

        assertTrue(model.tests().isEmpty());
    }

    @Test
    void doesNotDuplicateTestDiscoveredByParametrizeAndConvention() throws Exception {
        write("tests/test_users.py", """
                import pytest

                @pytest.mark.parametrize("value", [1, 2])
                def test_loads(value):
                    pass
                """);

        RepositoryModel model = analyze("tests/test_users.py");

        long count = model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId())
                        .equals("test_loads"))
                .count();

        assertEquals(1, count);
    }

    private io.repolens.core.model.Test testNamed(
            RepositoryModel model,
            String symbolName
    ) {
        return model.tests().stream()
                .filter(test -> symbolName(model, test.symbolId())
                        .equals(symbolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "tests=" + model.tests()
                                + " symbols=" + model.symbols()));
    }

    private static String symbolName(
            RepositoryModel model,
            String symbolId
    ) {
        return model.findSymbol(symbolId).orElseThrow().name();
    }

    private void write(String name, String source) throws Exception {
        Path file = tempDir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private RepositoryModel analyze(String... names) throws Exception {
        List<WorkingTreeInventory.InventoriedFile> files = new ArrayList<>();

        for (String name : names) {
            files.add(new WorkingTreeInventory.InventoriedFile(name, 200));
        }

        WorkingTreeInventory inventory =
                new WorkingTreeInventory(files, 800, 0);

        return new ProfiledSourceAnalyzer(
                new UnavailableSyntaxEngine(),
                LanguageProfiles.defaults()
        ).analyze(
                Repository.local("r1", "demo", tempDir.toString()),
                tempDir,
                inventory
        );
    }
}
