package io.repolens.parse.structural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringStructuralFactExtractorTest {

    @Test
    void combinesClassAndMethodRoutes() {
        assertEquals("/api/users", SpringStructuralFactExtractor.combineRoutes("/api", "/users"));
        assertEquals("/api/users", SpringStructuralFactExtractor.combineRoutes("/api/", "users"));
        assertEquals("/api", SpringStructuralFactExtractor.combineRoutes("/api", "/"));
        assertEquals("/users", SpringStructuralFactExtractor.combineRoutes("", "/users"));
    }

    @Test
    void humanizesRouteTail() {
        assertEquals("Users", SpringStructuralFactExtractor.humanizeRoute("/api/users", "UserController"));
        assertEquals("User", SpringStructuralFactExtractor.humanizeRoute("/", "UserController"));
    }
}
