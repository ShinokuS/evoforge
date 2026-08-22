package io.github.evoforge.simulation.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class MovementDefinitionCompilerTest {

    @Test
    void hasMovementKey() {
        MovementDefinitionCompiler compiler =
                new MovementDefinitionCompiler(
                        new MovementDefinitions());

        assertEquals(
                "movement",
                compiler.key());
    }

    @Test
    void compilesPositiveIntegerRate() {
        MovementDefinitions definitions =
                new MovementDefinitions();
        MovementDefinitionCompiler compiler =
                new MovementDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(3);

        compiler.compile(
                id,
                parse("""
                        {
                            "rate": 300
                        }
                        """),
                null);

        assertTrue(definitions.has(id));
        assertEquals(
                300,
                definitions.rate(id).unitsPerTick());
    }

    @Test
    void rejectsFractionalRate() {
        MovementDefinitionCompiler compiler =
                new MovementDefinitionCompiler(
                        new MovementDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(0),
                        parse("""
                                {
                                    "rate": 1.5
                                }
                                """),
                        null));
    }

    @Test
    void rejectsNonPositiveRate() {
        MovementDefinitionCompiler compiler =
                new MovementDefinitionCompiler(
                        new MovementDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(0),
                        parse("""
                                {
                                    "rate": 0
                                }
                                """),
                        null));
    }

    @Test
    void finishFreezesDefinitions() {
        MovementDefinitions definitions =
                new MovementDefinitions();
        MovementDefinitionCompiler compiler =
                new MovementDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(
                        ObjectDefinitionId.of(0),
                        MovementRate.of(100)));
    }

    private static JsonObject parse(
            String json) {
        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }
}
