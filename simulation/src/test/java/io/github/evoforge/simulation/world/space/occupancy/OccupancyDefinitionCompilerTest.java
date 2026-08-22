package io.github.evoforge.simulation.world.space.occupancy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class OccupancyDefinitionCompilerTest {

    @Test
    void hasOccupancyKey() {
        OccupancyDefinitionCompiler compiler =
                new OccupancyDefinitionCompiler(
                        new OccupancyDefinitions());

        assertTrue("occupancy".equals(compiler.key()));
    }

    @Test
    void compilesExclusiveFlag() {
        OccupancyDefinitions definitions =
                new OccupancyDefinitions();
        OccupancyDefinitionCompiler compiler =
                new OccupancyDefinitionCompiler(definitions);
        ObjectDefinitionId id = ObjectDefinitionId.of(3);

        compiler.compile(
                id,
                parse("""
                        {
                            "exclusive": true
                        }
                        """),
                null);

        assertTrue(definitions.requiresExclusiveCell(id));
        assertFalse(definitions.requiresExclusiveCell(
                ObjectDefinitionId.of(4)));
    }

    @Test
    void rejectsMissingOrNonBooleanExclusiveFlag() {
        OccupancyDefinitionCompiler compiler =
                new OccupancyDefinitionCompiler(
                        new OccupancyDefinitions());

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(0),
                        parse("{}"),
                        null));

        assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(
                        ObjectDefinitionId.of(1),
                        parse("""
                                {
                                    "exclusive": "yes"
                                }
                                """),
                        null));
    }

    @Test
    void finishFreezesDefinitions() {
        OccupancyDefinitions definitions =
                new OccupancyDefinitions();
        OccupancyDefinitionCompiler compiler =
                new OccupancyDefinitionCompiler(definitions);

        compiler.finish();

        assertTrue(definitions.isFrozen());
        assertThrows(
                IllegalStateException.class,
                () -> definitions.put(
                        ObjectDefinitionId.of(0),
                        true));
    }

    private static JsonObject parse(
            String json) {
        return JsonParser
                .parseString(json)
                .getAsJsonObject();
    }
}
