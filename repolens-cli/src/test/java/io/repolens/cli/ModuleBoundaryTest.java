package io.repolens.cli;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces modular boundaries across the assembled classpath.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.repolens", "ch.usi.si.seart.treesitter");
    }

    @Test
    void analyzersMustNotDependOnParse() {
        noClasses()
                .that().resideInAPackage("io.repolens.analyzers..")
                .should().dependOnClassesThat().resideInAPackage("io.repolens.parse..")
                .check(classes);
    }

    @Test
    void coreMustNotDependOnAdaptersOrParseImpl() {
        noClasses()
                .that().resideInAPackage("io.repolens.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.repolens.cli..",
                        "io.repolens.web..",
                        "io.repolens.parse..",
                        "io.repolens.ingest..",
                        "io.repolens.analyzers..",
                        "ch.usi.si.seart.treesitter..",
                        "io.javalin.."
                )
                .check(classes);
    }

    @Test
    void adaptersMustNotDependOnTreeSitterTypes() {
        noClasses()
                .that().resideInAnyPackage("io.repolens.cli..", "io.repolens.web..", "io.repolens.analyzers..")
                .should().dependOnClassesThat().resideInAPackage("ch.usi.si.seart.treesitter..")
                .check(classes);
    }

    @Test
    void adaptersExistOnClasspathForBoundaryChecks() {
        boolean hasWeb = classes.stream().anyMatch(c -> c.getPackageName().startsWith("io.repolens.web"));
        boolean hasParse = classes.stream().anyMatch(c -> c.getPackageName().startsWith("io.repolens.parse"));
        org.junit.jupiter.api.Assertions.assertTrue(hasWeb, "expected web classes on test classpath");
        org.junit.jupiter.api.Assertions.assertTrue(hasParse, "expected parse classes on test classpath");
    }
}
