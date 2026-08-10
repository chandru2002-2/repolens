package io.repolens.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepoLensWebMainTest {

    @Test
    void defaultsTo8080WhenNoArgsAndNoEnv() {
        assertEquals(8080, RepoLensWebMain.resolvePort(new String[]{}, null));
        assertEquals(8080, RepoLensWebMain.resolvePort(new String[]{}, "  "));
    }

    @Test
    void usesPortEnvironmentWhenPresent() {
        assertEquals(10000, RepoLensWebMain.resolvePort(new String[]{}, "10000"));
        assertEquals(1234, RepoLensWebMain.resolvePort(new String[]{}, " 1234 "));
    }

    @Test
    void cliPortOverridesEnvironment() {
        assertEquals(9090, RepoLensWebMain.resolvePort(new String[]{"--port", "9090"}, "10000"));
    }

    @Test
    void rejectsInvalidPortValues() {
        assertThrows(IllegalArgumentException.class,
                () -> RepoLensWebMain.resolvePort(new String[]{}, "abc"));
        assertThrows(IllegalArgumentException.class,
                () -> RepoLensWebMain.resolvePort(new String[]{"--port", "x"}, null));
    }
}
