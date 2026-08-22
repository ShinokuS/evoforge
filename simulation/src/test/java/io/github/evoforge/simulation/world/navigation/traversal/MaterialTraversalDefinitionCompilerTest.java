package io.github.evoforge.simulation.world.navigation.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import org.junit.jupiter.api.Test;

final class MaterialTraversalDefinitionCompilerTest {

    @Test
    void hasTraversalKey() {
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(
                        new MaterialTraversalDefinitions());

        assertEquals(
                "traversal",
                compiler.key());
    }

    @Test
    void compilesPositiveIntegerCost() {
        MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(definitions);
        MaterialDefinitionId id = MaterialDefinitionId.of(2);

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
        MaterialTraversalDefinitions definitions =
                new MaterialTraversalDefinitions();
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
    }

    @Test
    void rejectsMissingCost() {
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(
                        new MaterialTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        MaterialDefinitionId.of(0),
                        parse("{}"),
                        null));
    }

    @Test
    void rejectsFractionalCost() {
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(
                        new MaterialTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        MaterialDefinitionId.of(0),
                        parse("{\"cost\": 1.5}"),
                        null));
    }

    @Test
    void rejectsNonPositiveCost() {
        MaterialTraversalDefinitionCompiler compiler =
                new MaterialTraversalDefinitionCompiler(
                        new MaterialTraversalDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        MaterialDefinitionId.of(0),
                        parse("{\"cost\": 0}"),
                        null));
    }

    @Test
    void rejectsNullDefinitions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MaterialTraversalDefinitionCompiler(null));
    }

    private static JsonObject parse(
            String json) {

        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }
}
