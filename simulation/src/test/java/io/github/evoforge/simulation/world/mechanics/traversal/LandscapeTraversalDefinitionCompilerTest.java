package io.github.evoforge.simulation.world.mechanics.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import org.junit.jupiter.api.Test;

final class LandscapeTraversalDefinitionCompilerTest {

    @Test
    void hasTraversalKey() {
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(
                        new LandscapeTraversalDefinitions());

        assertEquals(
                "traversal",
                compiler.key());
    }

    @Test
    void compilesPositiveIntegerCost() {
        LandscapeTraversalDefinitions definitions =
                new LandscapeTraversalDefinitions();
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(definitions);
        LandscapeDefinitionId id = LandscapeDefinitionId.of(2);

        compiler.compile(
                id,
                parse("""
                        {
                            "cost": 1600
                        }
                        """),
                null);

        assertTrue(definitions.has(id));
        assertEquals(
                1600,
                definitions.cost(id).units());
    }

    @Test
    void finishFreezesDefinitions() {
        LandscapeTraversalDefinitions definitions =
                new LandscapeTraversalDefinitions();
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsMissingCost() {
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(
                        new LandscapeTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("{}"),
                        null));
    }

    @Test
    void rejectsFractionalCost() {
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(
                        new LandscapeTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("{\"cost\": 1.5}"),
                        null));
    }

    @Test
    void rejectsNonPositiveCost() {
        LandscapeTraversalDefinitionCompiler compiler =
                new LandscapeTraversalDefinitionCompiler(
                        new LandscapeTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        LandscapeDefinitionId.of(0),
                        parse("{\"cost\": 0}"),
                        null));
    }

    @Test
    void rejectsNullDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandscapeTraversalDefinitionCompiler(null));
    }

    private static JsonObject parse(
            String json) {

        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }
}
